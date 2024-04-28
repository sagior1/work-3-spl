package bgu.spl.net.impl.tftp;

public interface Commands
{
    int initBlockNum = 0;
    byte[] act(byte[] message, TftpProtocol proc, TftpEncoderDecoder.OPCODES currOpCode);

    static Commands getCommand(TftpEncoderDecoder.OPCODES opcode)
    {
        switch (opcode)
        {
            case ACK:
                return new AckCommand();
            case DATA:
                return new DataCommand();
            default:
                return null;
        }
    }
}
