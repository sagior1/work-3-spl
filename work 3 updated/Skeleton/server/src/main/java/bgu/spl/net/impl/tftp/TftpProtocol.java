package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.impl.tftp.TftpEncoderDecoder.Opcode;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    private boolean shouldTerminate = false;
    int connectionId;
    private ConnectionsImpl<byte[]> connections;


    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId=connectionId;
        this.connections=(ConnectionsImpl<byte[]>)connections;
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

    
}
