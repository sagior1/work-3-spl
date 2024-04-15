package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.impl.tftp.TftpEncoderDecoder.Opcode;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;
import java.util.LinkedList;


public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    private boolean shouldTerminate = false;
    int connectionId;
    private ConnectionsImpl<byte[]> connections;
    int blocknum;
    LinkedList<byte[]> dataToSend;
    private Path serverPath;


    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId=connectionId;
        this.connections=(ConnectionsImpl<byte[]>)connections;
        blocknum=0;
        serverPath=Paths.get("").toAbsolutePath().resolve("Flies");
    }

    @Override
    public void process(byte[] message) {
        TftpEncoderDecoder.Opcode opcode = extractOpcode(message);
        switch (opcode) {
            case RRQ:
                //handleReadRequest(message);
            case WRQ:
                //handleWriteRequest(message);
            case DIRQ:
                break;
            case LOGRQ:
                logrq(message);
                break;
            case DELRQ:
                break;
            case DISC:
                disc();
                break;
            case DATA:
                data(message);
                break;
            case BCAST:
                break;
            case ACK:
                //handleAck(message);
                break;
            case ERROR:
                //handleError(message);
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
            //TODO Error
        }
        else{
            connections.addName(connectionId, name);
            //TODO ack
        }
    }
    private void disc(){
        if(!connections.clientExist(connectionId)){
            //TODO error
        }
        else{
            connections.disconnect(connectionId);
            //TODO ack
        }
    }
    private void ack(int num){
        short a = (short) num;
        connections.send(connectionId, new byte []{0,4,(byte)(a >> 8) , (byte)( a & 0xff )});
    }

    private void data(byte[] message){
        short length = (short) (((short) message[0] ) << 8 | ((short) message[1] ));//TODO delete
        short blockNum = (short) (((short) message[2] ) << 8 | ((short) message[3] ));//TODO delete
        byte[] data = Arrays.copyOfRange(message, 4, message.length);//TODO delete
        byte[] dataArray = Arrays.copyOfRange(message, 6, message.length);
        dataToSend.add(dataArray);
        blockNum++;
        ack(blockNum);
        if(dataArray.length<512){
            File uploadAddress = new File(filesPath + "/" + this.fileNameToWrite);
            try (FileOutputStream out = new FileOutputStream(addressToWrite, true)) {
                byte[] finalData = new byte[dataToWrite.size()];
                for (int i=0; i<finalData.length; i++){
                    finalData[i] = dataToWrite.removeFirst();
                }
                uploadAddress.createNewFile();
                out.write(finalData, 0, finalData.length);
                bcast(false, this.fileNameToWrite);
            } catch (IOException e) {
        }
    }

            private void wrq(byte[] fileNameBytes) {
        //TODO: see if the user is logged in
        String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
        if (containsFileWithName(fileName, "Flies" + File.separator)){
            //TODO: add error number5
        }
        else if ((fileName.contains("0"))){
            //TODO: add error number 0, Illegal file name - can't contain 0
        }
        else{
            //TODO: send ack
            //TODO: see if there is anything else to add here
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
}
