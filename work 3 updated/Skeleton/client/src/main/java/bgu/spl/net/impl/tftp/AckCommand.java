package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public class AckCommand implements Commands
{
    @Override
    public byte[] act(byte[] message, TftpProtocol proc, TftpEncoderDecoder.OPCODES currOpCode)
    {
        System.out.println("ACK " + proc.getBlockNumber());

        assert TftpProtocol.getOpcode(message) == TftpEncoderDecoder.OPCODES.ACK;

        if (currOpCode == TftpEncoderDecoder.OPCODES.LOGRQ ||
            currOpCode == TftpEncoderDecoder.OPCODES.DELRQ)
        {
            proc.setWakeUp(true);
            return null;
        }

        if(currOpCode == TftpEncoderDecoder.OPCODES.DISC)
        {
            proc.shutDown();
            proc.setWakeUp(true);
            return null;
        }

        // Sending WRQ to server
        try (RandomAccessFile file = new RandomAccessFile(proc.getFileName(), "r"))
        {
            long startOffset = (long) proc.getBlockNumber() * TftpProtocol.blockSize;

            file.seek(startOffset); // move file pointer to the start offset

            long lengthToRead = Math.min(TftpProtocol.blockSize, file.length() - startOffset);
            // Adjust length to read based on whether endOffset or EOF comes first

            if(lengthToRead > 0)
            {
                byte[] buffer = new byte[(int) lengthToRead];
                int bytesRead = file.read(buffer); // Read data into buffer

                if (bytesRead != -1)
                {
                    proc.raiseBlockNumber();
                    return Utils.concatenateByteArrays(TftpEncoderDecoder.OPCODES.DATA.ToByteArray(),
                                                       Utils.intToBytes(buffer.length),
                                                       Utils.intToBytes(proc.getBlockNumber() + 1),
                                                       buffer);
                }
                else
                {
                    proc.setWakeUp(true);
                }
            }
            else
            {
                proc.setWakeUp(true);
            }
        }
        catch (IOException | RuntimeException ignored) {}
        proc.resetBlockNumber();
        return new byte[0];
    }
}