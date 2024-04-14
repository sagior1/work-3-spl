package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import java.util.ArrayList;
import java.util.List;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private List<Byte> bytes = new ArrayList<>();
    private Opcode opcode = Opcode.NONE;
    private int optExpectedLen = Integer.MAX_VALUE;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        if (bytes.size() >= optExpectedLen && nextByte == 0x0) {
            byte[] message = listToArray(poolBytes());
            setOpcode(Opcode.NONE);
            return message;
        } else {
            bytes.add(nextByte);
            if (bytes.size() == 2) {
                setOpcode(peekOpcode());
            }
            if (opcode == Opcode.DATA && bytes.size() == 4) {
                int size = (bytes.get(2) << 8) | (bytes.get(3) & 0xFF);
                optExpectedLen = 6 + size;
            }
            if (!haveAddedZero(opcode) && bytes.size() == optExpectedLen) {
                byte[] message = listToArray(poolBytes());
                setOpcode(Opcode.NONE);
                return message;
            }
            return null;
        }
    }

    @Override
    public byte[] encode(byte[] message) {
        Opcode opcode = Opcode.fromU16((message[0] << 8) | (message[1] & 0xFF));
        List<Byte> res = new ArrayList<>();
        switch (opcode) {
            case NONE:
                throw new IllegalArgumentException("Invalid opcode");
            case RRQ:
            case WRQ:
            case ERROR:
            case BCAST:
            case LOGRQ:
            case DELRQ:
                for (byte b : message) {
                    res.add(b);
                }
                res.add((byte) 0x0);
                break;
            case ACK:
            case DIRQ:
            case DISC:
            case DATA:
                for (byte b : message) {
                    res.add(b);
                }
                break;
        }
        return listToArray(res);
    }

    private List<Byte> poolBytes() {
        List<Byte> mes = new ArrayList<>(bytes);
        bytes.clear();
        return mes;
    }

    private void setOpcode(Opcode opcode) {
        this.opcode = opcode;
        switch (opcode) {
            case NONE:
                optExpectedLen = Integer.MAX_VALUE;
                break;
            case RRQ:
            case WRQ:
            case DIRQ:
            case LOGRQ:
            case DELRQ:
            case DISC:
                optExpectedLen = 2;
                break;
            case BCAST:
                optExpectedLen = 3;
                break;
            case ACK:
            case ERROR:
                optExpectedLen = 4;
                break;
            case DATA:
                optExpectedLen = 6;
                break;
        }
    }

    private Opcode peekOpcode() {
        int u16Opcode = (bytes.get(0) << 8) | (bytes.get(1) & 0xFF);
        return Opcode.fromU16(u16Opcode);
    }

    private boolean haveAddedZero(Opcode opcode) {
        switch (opcode) {
            case RRQ:
            case WRQ:
            case ERROR:
            case BCAST:
            case LOGRQ:
            case DELRQ:
            case NONE:
                return true;
            default:
                return false;
        }
    }

    private byte[] listToArray(List<Byte> byteList) {
        byte[] byteArray = new byte[byteList.size()];
        for (int i = 0; i < byteList.size(); i++) {
            byteArray[i] = byteList.get(i);
        }
        return byteArray;
    }

    public enum Opcode {
        NONE, RRQ, WRQ, DATA, ACK, ERROR, DIRQ, LOGRQ, DELRQ, BCAST, DISC;

        public static Opcode fromU16(int opcode) {
            switch (opcode) {
                case 1: return RRQ;
                case 2: return WRQ;
                case 3: return DATA;
                case 4: return ACK;
                case 5: return ERROR;
                case 6: return DIRQ;
                case 7: return LOGRQ;
                case 8: return DELRQ;
                case 9: return BCAST;
                case 10: return DISC;
                default: return NONE;
            }
        }
    }
}
