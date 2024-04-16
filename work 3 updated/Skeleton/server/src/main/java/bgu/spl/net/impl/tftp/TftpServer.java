package bgu.spl.net.impl.tftp;

import bgu.spl.net.srv.Server;

public class TftpServer {
    public static void main(String[] args) {
        if(args.length>1){
            return;
        }
        int port = 7777;
        if(args.length==1){
         port = Integer.parseInt(args[0]);
        }
        Server.threadPerClient(  
                port, //port
                (() -> new TftpProtocol()), //protocol factory
                TftpEncoderDecoder::new //message encoder decoder factory
        ).serve();
        
    }
}
