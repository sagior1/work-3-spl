package bgu.spl.net.impl.tftp;

import java.math.BigInteger;

public class Utils
{
    public static final int dataOffset = 6;
    public static byte[] concatenateByteArrays(byte[]... arrays)
    {
        // Calculate the total length of the concatenated array
        int totalLength = 0;
        for (byte[] array : arrays)
        {
            totalLength += array.length;
        }

        // Create a new array to hold the concatenated bytes
        byte[] result = new byte[totalLength];

        // Copy elements from each array to the result array
        int offset = 0;
        for (byte[] array : arrays)
        {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }

        return result;
    }

    public static byte[] intToBytes(int num)
    {
        byte[] n = BigInteger.valueOf(num).toByteArray();

        if (n.length == 1)
        {
            byte number = (byte) num;
            return new byte[]{(byte) 0, number};
        }

        return n;
    }

    public static short byteArrayToShortConvert(byte[] data)
    {
        return ( short ) (((short) data[0]) << 8 | ( short ) ( data [1]) );
    }

    public static int getDataBlockNumber(byte[] message)
    {

        return ((message[dataOffset - 2] & 0xFF) << 8) | (message[dataOffset - 1] & 0xFF);
    }
    public static int getAckBlockNumber(byte[] message)
    {

        return ((message[TftpProtocol.errorCodeOffset - 2] & 0xFF) << 8) | (message[TftpProtocol.errorCodeOffset - 1] & 0xFF);
    }
}
