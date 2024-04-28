package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessagingProtocol;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class TftpProtocol implements MessagingProtocol<byte[]>
{
    private byte[] data = new byte[0];
    final static int opCodeOffset = 2;
    final static int blockSize = 512;
    final static int errorCodeOffset = 4;
    final static int opCodePrefix = 0;

    boolean shouldTerminate = false;
    boolean shouldWakeUp = false;
    private int blockNumber = 0;
    static String fileName = "";

    final static String dirPath = System.getProperty("user.dir");

    private TftpEncoderDecoder.OPCODES currOpCode = TftpEncoderDecoder.OPCODES.NONE;

    @Override
    public byte[] process(byte[] msg)
    {
        TftpEncoderDecoder.OPCODES opCode = getOpcode(msg);

        if (opCode == TftpEncoderDecoder.OPCODES.NONE)
        {
//            shouldWakeUp = true;
           return null;
        }

        if(opCode == TftpEncoderDecoder.OPCODES.ERROR)
        {
            printError(msg);
            shouldWakeUp = true;
            return null;
        }

        if(opCode == TftpEncoderDecoder.OPCODES.BCAST)
        {
            printBcast(msg);
            return null;
        }

        Commands command = Commands.getCommand(opCode);

        assert command != null;
        return command.act(msg, this, currOpCode);
    }

    private void printError(byte[] msg)
    {
        assert getOpcode(msg) == TftpEncoderDecoder.OPCODES.ERROR;

        deleteFileIfNeeded();
        byte[] errCodeBytes = Arrays.copyOfRange(msg, opCodeOffset, errorCodeOffset);
        short errCode = Utils.byteArrayToShortConvert(errCodeBytes);

        System.out.println("Error " + errCode + " " + getErrorString(msg));
    }

    private void printBcast(byte[] msg)
    {
        assert getOpcode(msg) == TftpEncoderDecoder.OPCODES.BCAST;
        short bcastType = msg[opCodeOffset];

        System.out.println("BCAST " + (bcastType == 0 ? "del" : "add") + " " + getBcastString(msg));
    }
    void setCurrOpCode(TftpEncoderDecoder.OPCODES currOpCode)
    {
        this.currOpCode = currOpCode;
    }

    static TftpEncoderDecoder.OPCODES getOpcode(byte[] data)
    {
        assert data.length >= 2; //Msg with opcode is at least 2 bytes
        return TftpEncoderDecoder.OPCODES.valueOf((byte) (((data[0] & 0xFF) << 8) | (data[1] & 0xFF)));
    }

    String getErrorString(byte[] message)
    {
        byte[] name = Arrays.copyOfRange(message, errorCodeOffset, message.length);
        return new String(name, StandardCharsets.UTF_8);
    }

    String getBcastString(byte[] message)
    {
        byte[] name = Arrays.copyOfRange(message, opCodeOffset + 1, message.length);
        return new String(name, StandardCharsets.UTF_8);
    }

    @Override
    public boolean shouldTerminate()
    {
        return shouldTerminate;
    }

    String getFileName()
    {
        return fileName;
    }

    void setFileName(String name)
    {
        fileName = name;
    }

    byte[] createAckRequest(int blockNumber)
    {
        return Utils.concatenateByteArrays(TftpEncoderDecoder.OPCODES.ACK.ToByteArray(),
                                           new byte[]{opCodePrefix,(byte) blockNumber});
    }

    void setData(byte[] data)
    {
        this.data = data;
    }

    byte[] getData()
    {
        return data;
    }

    void shutDown()
    {
         shouldTerminate = true;
    }

    public boolean shouldWakeUp()
    {
        return shouldWakeUp;
    }

    public void setWakeUp(boolean val)
    {
        shouldWakeUp = val;
    }

    private void deleteFileIfNeeded()
    {
        if (currOpCode == TftpEncoderDecoder.OPCODES.RRQ)
        {
            File file = new File(dirPath + "/" + fileName);

            if (file.exists() && file.isFile() && file.getParentFile().canWrite() && file.canWrite())
            {
                if(!file.delete())
                {
                    System.out.println("failed to delete file after error");
                }
            }
        }
    }

    public int getBlockNumber()
    {
        return blockNumber;
    }

    public void raiseBlockNumber()
    {
        ++blockNumber;
    }

    public void resetBlockNumber()
    {
        blockNumber = 0;
    }
}