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

import javax.print.DocFlavor.STRING;


public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    private boolean shouldTerminate = false;
    int connectionId;
    private ConnectionsImpl<byte[]> connections;
    int blockNum;
    LinkedList<byte[]> dataToUpload;
    LinkedList<Byte> dataToSend;
    boolean dataSentIsRRQ;
    private Path serverPath;
    private String fileName;
    String[] errorMesseges;


    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId=connectionId;
        this.connections=(ConnectionsImpl<byte[]>)connections;
        blockNum=0;
        dataSentIsRRQ=false;
        serverPath=Paths.get("").toAbsolutePath().resolve("Flies");
        dataToUpload = new LinkedList<byte[]>();
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
                delrq(message);
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
                System.out.println("defoult");
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
            ack(0);
            String msg = new String("it will work!");
            byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
            bcast(true, bytes);
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
        System.out.println("try to get the data");
        System.out.println(blockNum);
        byte[] dataArray = Arrays.copyOfRange(message, 6, message.length);
        dataToUpload.add(dataArray);
        blockNum++;//TODO remember to inizialize to 0 also not sure if is needed

         //TODO - delete test
        
        System.out.println(blockNum);
        ack(blockNum);
        if(dataArray.length<512){
            File uploadAddress = new File(serverPath + File.separator + this.fileName);//will be fine after add rotem's part
            //TODO - delete the test
            System.out.println(uploadAddress.toString());  

            try (FileOutputStream out = new FileOutputStream(uploadAddress, true)) {
                uploadAddress.createNewFile();
                for (byte[] data : dataToUpload) {
                    out.write(data); // Write each byte array segment to the file
                }
                //ack(blockNum); // TODO - good?
                blockNum=0;//TODO not sure if this is needed
                bcast(false, this.fileName.getBytes());
                dataToUpload.clear();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
        

   //request to add a file, so we check if it can be added
    private void wrq(byte[] fileNameBytes) {
        this.fileName = extractName(fileNameBytes);
        if(!connections.clientExist(connectionId)){
            error((short) 6 , errorMesseges[6]);
        }
        else{
            //If the file already exists on the server, return an error.
            if (containsFileWithName(fileName, "Flies" + File.separator)){
                //TODO - delete comment
                System.err.println("the file: " + fileName + "already exists in server");
                error((short) 5, errorMesseges[5]);
            }
            else if ((fileName.contains("0"))){
                System.out.println("the file " + fileName + "contains 0");
                error((short) 0, errorMesseges[0]);
            }
            else{
                //TODO - delete test
                System.err.println("wrq - needs to send ack");
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
    // public static boolean containsFileWithName(String inputString, String directoryPath) {
    //     File directory = new File(directoryPath);
    //     if (directory.exists() && directory.isDirectory()) {
    //         List<String> fileList = Arrays.asList(directory.list());
    //         if (fileList.contains(inputString)){
    //             return true;
    //         }
    //     }
    //     return false;
    //  }
    public static boolean containsFileWithName(String inputString, String directoryPath) {
        //TODO - delete the comment
        System.out.println("searching for: " + inputString + "\n in: " + directoryPath);
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            System.out.println("Directory does not exist: " + directoryPath);
            return false;
        }
        if (!directory.isDirectory()) {
            System.out.println("Given path is not a directory: " + directoryPath);
            return false;
        }
    
        try {
            List<String> fileList = Arrays.asList(directory.list());
            System.out.println("Files in directory: " + fileList);
            System.out.println(inputString);
            System.out.println(fileList.contains(inputString) + " hey");
            return fileList.contains(inputString);
        } catch (SecurityException e) {
            System.out.println("Access denied to directory: " + directoryPath);
            e.printStackTrace();
            return false;
        }
    }
     private void rrq(byte[] message){
        System.out.println("entered rrq in tftp server protocol");
        fileName = extractName(message);
        dataSentIsRRQ=true;
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
        blockNum++;
        System.out.println("entred sendData inside server protocol");
        if (dataToSend.size() <= 512){
            byte[] byteArray = new byte[dataToSend.size()+6];
            byteArray[0] = 0;
            byteArray[1] = 3;
            byteArray[2] = (byte) (dataToSend.size() >> 8);
            byteArray[3] = (byte) dataToSend.size();
            byteArray[4] = 0;
            byteArray[5] = (byte) blockNum;
            System.out.println("supposed to finish cuz we are in block number: "+ blockNum);
            for (int i=6;!dataToSend.isEmpty();i++) {
                byteArray[i] = dataToSend.remove();  // Auto-unboxing Byte to byte
            }
            blockNum=0;
            dataToSend.clear();
            if (dataSentIsRRQ) {
                System.out.println("RRQ "+fileName+ " complete");
            }
            connections.send(connectionId, byteArray);
        }
        else{
            System.out.println("block number: "+ blockNum);
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
            //blockNum++;
            connections.send(connectionId, byteArray);
        }
     }
     public void error(short code, String errorMessege) {
        byte[] errorMessageBytes = errorMessege.getBytes(StandardCharsets.UTF_8);
        short opCode=5;
        byte[] errorCodeBytes = new byte[2];
        errorCodeBytes[0] = (byte) ((code >> 8) & 0xFF);
        errorCodeBytes[1] = (byte) (code & 0xFF);
        byte[] errorPacket = new byte[errorMessageBytes.length +4];
        //first 2 byte - opCode
        errorPacket[0] = (byte) ((opCode >> 8) & 0xFF);
        errorPacket[1] = (byte) (opCode & 0xFF);
        //bytes 3-4 - error code
        errorPacket[2] = errorCodeBytes[0];
        errorPacket[3] = errorCodeBytes[1];
        //combine the messege and the first bytes
        System.arraycopy(errorMessageBytes, 0, errorPacket, 4, errorMessageBytes.length);
        //add 0 to the end of the packet
        //errorPacket[errorPacket.length-1] = 0;
        connections.send(connectionId, errorPacket);
    }

      private void delrq(byte[] message){
        String fileNameTODelete = extractName(message);
         //TODO - ניסיון
        byte[] dataMsg = fileNameTODelete.getBytes();


        System.out.println(this.serverPath);
        if(!connections.clientExist(connectionId)){
            error((short) 6 , errorMesseges[6]);
        }
        else{
            if (containsFileWithName(fileNameTODelete,"Flies" + File.separator)){
                //TODO-delete
                System.out.println("Found the file " + fileNameTODelete + " and will delete it");

                File fileToDelete = new File(serverPath + File.separator + fileNameTODelete);
                //TODO-delete
                System.out.println(fileNameTODelete);
                System.out.println(fileToDelete.getAbsolutePath());  
                System.out.println(fileToDelete.exists());
                //boolean isDeleted = fileToDelete.delete();
                //System.err.println(fileToDelete.delete());

                if (fileToDelete.delete()){
                    System.out.println("the file: " + fileNameTODelete + " has been deleted successfully");
                    ack(0);
                    bcast(true, dataMsg);
                }
                else {System.err.println("we couldnt delete the file: " + fileNameTODelete);}
                
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
        //TODO - delete test
        System.err.println("problem here?1");

        System.arraycopy(message,0,data,3,message.length);
        ConcurrentHashMap<Integer,BlockingConnectionHandler<byte[]>> clients=connections.getclientsMap();

        System.err.println("problem here?2");
        for (Integer id : clients.keySet()) {
            System.err.println("problem here?3");

             connections.send(id,data);
        }
    }
    private void recieveAck(byte[] message){
        if(!dataToSend.isEmpty()){
            short ackNum = (short) (((short) message[0] & 0xFF) << 8 | (short) (message[1] & 0xFF));
            System.out.println("ACK recieved num: " + (ackNum));
            sendData();
        }
    }
    
    private void dirq(){
        if(!connections.clientExist(connectionId)){
            error((short) 6 , errorMesseges[6]);
        }
        else{
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
                //TODO - delete the test
                    // //test
                    // System.out.println("Array: " + Arrays.toString(dir));
                    // //test
            this.dataToSend = arrayToList(dir);
            sendData();
    }
    }

        /**
    public static String extractName(byte[] array) {//TODO copied from ron so needs to be changed
 * converts a byte[] array to a List<Bytes>
        int length = array.length;
 *
        byte[] usernameBytes = new byte[length - 2]; // excluding opcode and terminator
 * @param byteArray      the byte[] array we want to convert
        System.arraycopy(array, 2, usernameBytes, 0, length - 2);
 * @return              same data in List<Bytes>
        return new String(usernameBytes, StandardCharsets.UTF_8);
 */
public static LinkedList<Byte> arrayToList(byte[] byteArray) {
    LinkedList<Byte> byteLinkedList = new LinkedList<>();
    for (byte b : byteArray) {
        byteLinkedList.add(b);
    }
    return byteLinkedList;
}


    //extarcting the file name (for rrq etc)
    public static String extractName(byte[] array) {//TODO copied from ron so needs to be changed
        int length = array.length;
        byte[] usernameBytes = new byte[length - 2]; // excluding opcode and terminator
        System.arraycopy(array, 2, usernameBytes, 0, length - 2);
        return new String(usernameBytes, StandardCharsets.UTF_8);
    }


}
