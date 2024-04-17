package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.impl.tftp.TftpEncoderDecoder.Opcode;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    private boolean shouldTerminate = false;
    int connectionId;
    private ConnectionsImpl<byte[]> connections;
    int blockNum;
    LinkedList<byte[]> dataToUpload;
    LinkedList<Byte> dataToSend;
    private Path serverPath;
    private String fileName;
    String[] errorMesseges;


    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        System.out.println("entered start in TFTPPROTOCOL");
        this.connectionId=connectionId;
        this.connections=(ConnectionsImpl<byte[]>)connections;
        blockNum=0;
        serverPath=Paths.get("").toAbsolutePath().resolve("Flies");
        this.errorMesseges = new String[]{
            "Not defined, see error message (if any).",
            "File not found – RRQ DELRQ of non-existing file.",
            "Access violation – File cannot be written, read or deleted.",
            "Disk full or allocation exceeded – No room in disk.",
            "Illegal TFTP operation – Unknown Opcode.",
            "File already exists – File name exists on WRQ.",
            "User not logged in – Any opcode received before Login completes.",
            "User already logged in – Login username already connected."};
    }

    @Override
    public void process(byte[] message) {
        TftpEncoderDecoder.Opcode opcode = extractOpcode(message);
        switch (opcode) {
            case RRQ:
                rrq(message);
                break;
            case WRQ:
                wrq(message);
                break;
            case DIRQ:
                dirq();
                break;
            case LOGRQ:
                logrq(message);
                break;
            case DELRQ:
                delq(message);
                break;
            case DISC:
                disc();
                break;
            case DATA:
                data(message);
                break;
            case ACK:
                recieveAck(message);
                break;
            // Add cases for other opcodes
            default:
                break;//אולי לעשות פה משהו אחר
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    } 
    private TftpEncoderDecoder.Opcode extractOpcode(byte[] data) {
        // Extract opcode assuming data is not null and has at least two bytes
        int opCodeNum = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        return TftpEncoderDecoder.Opcode.fromU16(opCodeNum);
    }
    private void logrq(byte[] message){
        String name=new String(message);
        if(connections.clientExist(name)||connections.clientExist(connectionId)){
            error((short) 7, errorMesseges[7]);
        }
        else{
            connections.addName(connectionId, name);
            System.out.println("client supposed to be logged in");
            ack(0);
        }
    }
    private void disc(){
        if(!connections.clientExist(connectionId)){
            error((short) 6, errorMesseges[6]);
        }
        else{
            ack(0);
            connections.disconnect(connectionId);
            shouldTerminate=true;
        }
    }
    private void ack(int num){
        short a = (short) num;
        connections.send(connectionId, new byte []{0,4,(byte)(a >> 8) , (byte)( a & 0xff )});
    }

    //recieve the data of a file from the client and upload it (after the cliet did write request)
    private void data(byte[] message){
        byte[] dataArray = Arrays.copyOfRange(message, 6, message.length);
        dataToUpload.add(dataArray);
        blockNum++;//TODO remember to inizialize to 0 also not sure if is needed
        ack(blockNum);
        if(dataArray.length<512){
            File uploadAddress = new File(serverPath + "/" + this.fileName);//will be fine after add rotem's part
            try (FileOutputStream out = new FileOutputStream(uploadAddress, true)) {
                uploadAddress.createNewFile();
                for (byte[] data : dataToUpload) {
                    out.write(data); // Write each byte array segment to the file
                }
                blockNum=0;//TODO not sure if this is needed
                bcast(false, message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
        

    //request to add a file, so we check if it can be added
    private void wrq(byte[] fileNameBytes) {
        if(!connections.clientExist(connectionId)){
            error((short) 6 , errorMesseges[6]);
        }
        else{
            this.fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
            if (containsFileWithName(fileName, "Flies" + File.separator)){
                error((short) 5, errorMesseges[5]);
            }
            else if ((fileName.contains("0"))){

                error((short) 0, errorMesseges[0]);
            }
            else{
                ack(0);
            }
        }
    }

    /**
 * Checks if a file with the specified name exists within the given directory.
 * 
 * @param fileName      The name of the file to check for.
 * @param directoryPath The path of the directory to search for the file (will always be "Flies")
 * @return              true if a file with the specified name exists in the directory, false otherwise.
 */
    public static boolean containsFileWithName(String inputString, String directoryPath) {
        File directory = new File(directoryPath);
        if (directory.exists() && directory.isDirectory()) {
            List<String> fileList = Arrays.asList(directory.list());
            if (fileList.contains(inputString)){
                return true;
            }
        }
        return false;
     }

     private void rrq(byte[] message){
        String fileName = new String(message, StandardCharsets.UTF_8);
        if(!connections.clientExist(connectionId)){
            error((short) 6 , errorMesseges[6]);
        }
        else{
            if(!containsFileWithName(fileName, "Flies"+File.separator)){
                error((short) 1, errorMesseges[1]);
            }
            else{
                Path filePath = serverPath.resolve(fileName);
            try {
                byte[] fileBytes = Files.readAllBytes(filePath);
                LinkedList<Byte> data = new LinkedList<>();
                for (byte b : fileBytes) {
                    data.add(b);
                }
                this.dataToSend = data;
                sendData();
            } catch (IOException e) {
                e.printStackTrace();
            }
            }
        }
     }

    //send data to client if its dirq or rrq
     private void sendData(){
        
        if (dataToSend.size() <= 512){
            byte[] byteArray = new byte[dataToSend.size()];
            byteArray[0] = 0;
            byteArray[1] = 3;
            byteArray[2] = (byte) (dataToSend.size() >> 8);
            byteArray[3] = (byte) dataToSend.size();
            byteArray[4] = 0;
            byteArray[5] = (byte) blockNum;
            for (int i=6;i<518;i++) {
                byteArray[i] = dataToSend.remove();  // Auto-unboxing Byte to byte
            }
            blockNum=0;
            dataToSend.clear();
            connections.send(connectionId, byteArray);
        }
        else{
            byte[] byteArray = new byte[512+6];
            byteArray[0] = 0;
            byteArray[1] = 3;
            byteArray[2] = (byte) (512 >> 8);
            byteArray[3] = (byte) 512;
            byteArray[4] = 0;
            byteArray[5] = (byte) blockNum;
            for (int i=6;i<518;i++) {
                byteArray[i] = dataToSend.remove();  // Auto-unboxing Byte to byte
            }
            blockNum++;
            connections.send(connectionId, byteArray);
        }
     }
     public void error(short code, String errorMessege) {
        byte[] errorMessageBytes = errorMessege.getBytes(StandardCharsets.UTF_8);
        short opCode=5;
        byte[] errorCodeBytes = new byte[2];
        errorCodeBytes[0] = (byte) ((code >> 8) & 0xFF);
        errorCodeBytes[1] = (byte) (code & 0xFF);
        byte[] errorPacket = new byte[errorMessageBytes.length +5];
        //first 2 byte - opCode
        errorPacket[0] = (byte) ((opCode >> 8) & 0xFF);
        errorPacket[1] = (byte) (opCode & 0xFF);
        //bytes 3-4 - error code
        errorPacket[2] = errorCodeBytes[0];
        errorPacket[3] = errorCodeBytes[1];
        //combine the messege and the first bytes
        System.arraycopy(errorMessageBytes, 0, errorPacket, 4, errorMessageBytes.length);
        //add 0 to the end of the packet
        errorPacket[errorPacket.length-1] = 0;
        connections.send(connectionId, errorPacket);
    }

     private void delq(byte[] message){
        String fileNameTODelete = new String(message);
        if(!connections.clientExist(connectionId)){
            error((short) 6 , errorMesseges[6]);
        }
        else{
            if (containsFileWithName(fileNameTODelete,"Flies" + File.separator)){
                File fileToDelete = new File(fileNameTODelete);
                String filePath = fileToDelete.getAbsolutePath();
                Path pathToDelete = Paths.get(filePath);
                try {
                Files.delete(pathToDelete);
                ack(0);
                bcast(true, message);
            } catch (IOException e) {}
            }
            else{
                error((short) 1, errorMesseges[1]);
            }
        }
    }

    private void bcast(boolean isDeleted,byte[] message){
        byte[] data=new byte[3 + message.length];
        data[0]=0;//adding op code
        data[1]=9;
        String file=new String(message, java.nio.charset.StandardCharsets.UTF_8);
        if(isDeleted){//adding byte if for delete or
            data[2]=0;
            System.out.println("BCAST remove " + file);
        }
        else{
            data[2]=1;
            System.out.println("WRQ "+fileName+" complete");
            System.out.println("BCAST add "+fileName);
        }
        System.arraycopy(message,0,data,3,message.length);
        ConcurrentHashMap<Integer,BlockingConnectionHandler<byte[]>> clients=connections.getclientsMap();
        for (Integer id : clients.keySet()) {
             connections.send(id,data);
        }
    }
    private void recieveAck(byte[] message){
        sendData();
        short ackNum = (short) (((short) message[0] & 0xFF) << 8 | (short) (message[1] & 0xFF));
        System.out.println("ACK " + (ackNum));
    }
    
    private void dirq(){
        //TODO - check if the user is logged in
        File directory = new File("flies" + File.separator);
        List<String> fileList = Arrays.asList(directory.list());
        List<byte[]> dirInBytes = new ArrayList<>();

        // Iterate through each filename, and convert the filename in string to bytes
        for (String filename : fileList) {
            byte[] filenameBytes = filename.getBytes(StandardCharsets.UTF_8);
            dirInBytes.add(filenameBytes);
            dirInBytes.add("\n".getBytes(StandardCharsets.UTF_8));
        }
        //convert the list of bytes to an array of bytes
        int totalLength = 0;
        // Calculate the total length of the byte array
        for (byte[] bytes : dirInBytes) {
            totalLength += bytes.length;
        }

        byte[] dir = new byte[totalLength];

        int currentIndex = 0;
        // Copy each byte array from the list to the result array
        for (byte[] bytes : dirInBytes) {
            System.arraycopy(bytes, 0, dir, currentIndex, bytes.length); 
            currentIndex += bytes.length;
        }

        connections.send(this.connectionId, dir);
    }

 
 
}
