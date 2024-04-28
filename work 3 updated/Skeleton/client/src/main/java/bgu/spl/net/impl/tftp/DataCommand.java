package bgu.spl.net.impl.tftp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import static bgu.spl.net.impl.tftp.TftpProtocol.dirPath;
import static bgu.spl.net.impl.tftp.TftpProtocol.fileName;

public class DataCommand implements Commands
{
    @Override
    public byte[] act(byte[] message, TftpProtocol proc, TftpEncoderDecoder.OPCODES currOpCode)
    {
        assert currOpCode == TftpEncoderDecoder.OPCODES.DIRQ || currOpCode == TftpEncoderDecoder.OPCODES.RRQ;
        return currOpCode == TftpEncoderDecoder.OPCODES.DIRQ ? processDirq(message, proc) : processRrq(message, proc);
    }

    private byte[] processRrq(byte[] message,  TftpProtocol proc)
    {
        byte[] fileData = Arrays.copyOfRange(message, Utils.dataOffset, message.length);
        int blockNumber = ((message[Utils.dataOffset - 2] & 0xFF) << 8) | (message[Utils.dataOffset - 1] & 0xFF);

        try (OutputStream outStream = Files.newOutputStream((Paths.get(dirPath + "/" + fileName)), StandardOpenOption.APPEND))
            {
                outStream.write(fileData);
            }
            catch (IOException e)
            {
                return null;
            }

        if(fileData.length < TftpProtocol.blockSize)
        {
            proc.setWakeUp(true);
        }

        return proc.createAckRequest(blockNumber);
    }

    private byte[] processDirq(byte[] message,  TftpProtocol proc)
    {
        byte[] fileData = Arrays.copyOfRange(message, Utils.dataOffset, message.length);
        int dataLeft = ((message[Utils.dataOffset - 4] & 0xFF) << 8) | (message[Utils.dataOffset - 3] & 0xFF);
        int blockNumber = Utils.getDataBlockNumber(message);

        proc.setData(Utils.concatenateByteArrays(proc.getData(), fileData));

        if(dataLeft < TftpProtocol.blockSize)
        {
            System.out.print(new String(fileData, StandardCharsets.UTF_8).replace((char) 0, '\n'));
            proc.setData(new byte[]{});
            proc.setWakeUp(true);
        }

        return proc.createAckRequest(blockNumber);
    }
}
