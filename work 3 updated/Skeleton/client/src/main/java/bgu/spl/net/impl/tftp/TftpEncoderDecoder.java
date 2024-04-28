package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]>
{
    private static final int opCodeSize = 2;
    final List<Byte> byteStream = new ArrayList<>();
    //TODO: Implement here the TFTP encoder and decoder
    public enum OPCODES
    {
        NONE((byte) 0),
        RRQ((byte) 1)
                {
                    @Override
                    public boolean decode(byte nextByte, TftpEncoderDecoder encdec)
                    {
                        return encdec.byteStream.size() >= 3 && endsOnZero(nextByte);
                    }
                    @Override
                    public boolean shouldRemoveZero()
                    {
                        return true; // only 2 bytes
                    }
                },
        WRQ((byte) 2)
                {
                    @Override
                    public boolean decode(byte nextByte, TftpEncoderDecoder encdec)
                    {
                        return encdec.byteStream.size() >= 3 && endsOnZero(nextByte);
                    }
                    @Override
                    public boolean shouldRemoveZero()
                    {
                        return true; // only 2 bytes
                    }
                },
        DATA((byte) 3)
                {
                    @Override
                    public boolean decode(byte nextByte, TftpEncoderDecoder encdec)
                    {
                        if (encdec.byteStream.size() == 4)
                        {
                            int size = (encdec.byteStream.get(2) & 0xFF) << 8 | (encdec. byteStream.get(3) & 0xFF);
                            expectedLength = 6 + size;
                        }

                        return encdec.byteStream.size() == expectedLength;
                    }
                },
        ACK((byte) 4)
                {
                    @Override
                    public boolean decode(byte nextByte, TftpEncoderDecoder encdec)
                    {
                        return encdec.byteStream.size() == 4;
                    }
                },
        ERROR((byte) 5)
                {
                    @Override
                    public boolean decode(byte nextByte, TftpEncoderDecoder encdec)
                    {
                        return encdec.byteStream.size() >= 5 && endsOnZero(nextByte);
                    }
                    @Override
                    public boolean shouldRemoveZero()
                    {
                        return true; // only 2 bytes
                    }
                },
        DIRQ((byte) 6)
                {
                    @Override
                    public boolean decode(byte nextByte, TftpEncoderDecoder encdec)
                    {
                        return true; //only 2 bytes
                    }
                },
        LOGRQ((byte) 7)
                {
                    @Override
                    public boolean decode(byte nextByte, TftpEncoderDecoder encdec)
                    {
                        return encdec.byteStream.size() >= 3 && endsOnZero(nextByte);
                    }
                },
        DELRQ((byte) 8)
                {
                    @Override
                    public boolean decode(byte nextByte, TftpEncoderDecoder encdec)
                    {
                        return encdec.byteStream.size() >= 3 && endsOnZero(nextByte);
                    }
                    @Override
                    public boolean shouldRemoveZero()
                    {
                        return true; // only 2 bytes
                    }
                },
        BCAST((byte) 9)
                {
                    @Override
                    public boolean decode(byte nextByte, TftpEncoderDecoder encdec)
                    {
                        return encdec.byteStream.size() >= 4 && endsOnZero(nextByte);
                    }
                    @Override
                    public boolean shouldRemoveZero()
                    {
                        return true; // only 2 bytes
                    }
                },
        DISC((byte) 10)
                {
                    @Override
                    public boolean decode(byte nextByte, TftpEncoderDecoder encdec)
                    {
                        return true; // only 2 bytes
                    }
                };

        final byte opCode;
        int expectedLength = Integer.MAX_VALUE;
        OPCODES(byte i)
        {
            opCode = i;
        }

        public boolean decode(byte nextByte, TftpEncoderDecoder encdec)
        {
            return false;
        }
        public boolean shouldRemoveZero()
        {
            return false;
        }

        private static boolean endsOnZero(byte nextByte)
        {
            return nextByte == 0x0;
        }

        public static OPCODES valueOf(byte code)
        {
            for (OPCODES op : values())
            {
                if (op.opCode == code)
                {
                    return op;
                }
            }

            throw new IllegalArgumentException(String.valueOf(code));
        }

        public byte[] ToByteArray()
        {
            return new byte[]{(byte) 0,opCode};
        }
    }

    OPCODES opCode = OPCODES.NONE;

    @Override
    public byte[] decodeNextByte(byte nextByte)
    {
        byteStream.add(nextByte);

        if(byteStream.size() == opCodeSize)
        {
            opCode = OPCODES.valueOf(nextByte);
        }

        if(opCode.decode(nextByte, this))
        {
            if (opCode.shouldRemoveZero() && nextByte == 0x0)
            {
               byteStream.remove(byteStream.size() - 1); // Remove trailing 0 if exists
            }

            byte[] tmp = new byte[byteStream.size()];

            for (int i = 0; i < tmp.length; i++)
            {
                tmp[i] = byteStream.get(i);
            }

            opCode = OPCODES.NONE;
            opCode.expectedLength = Integer.MAX_VALUE;
            byteStream.clear();

            return tmp; // the right data
        }

        return null;
    }

    @Override
    public byte[] encode(byte[] message)
    {
        int opVal = ((message[0] & 0xFF) << 8) | (message[1] & 0xFF);
        OPCODES opcode = OPCODES.valueOf((byte) opVal);

        switch (opcode)
        {
            case LOGRQ:
            case RRQ:
            case WRQ:
            case DELRQ:
            case BCAST:
            case ERROR:
                byte[] res = new byte[message.length + 1]; // Add null terminator
                System.arraycopy(message, 0, res, 0, message.length);
                res[message.length] = (byte) 0x0;
                return res;
            case ACK:
            case DIRQ:
            case DATA:
            case DISC:
                return message;
            default:
                throw new IllegalArgumentException("Unknown opcode");
        }
    }
}