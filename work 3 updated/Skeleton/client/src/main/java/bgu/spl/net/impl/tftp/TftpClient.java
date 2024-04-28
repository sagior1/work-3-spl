package bgu.spl.net.impl.tftp;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

import static bgu.spl.net.impl.tftp.TftpProtocol.dirPath;

public class TftpClient
{
    private final static TftpProtocol protocol = new TftpProtocol();
    private final static TftpEncoderDecoder encdec = new TftpEncoderDecoder();
    private final static Object lock = new Object();
    private static TftpEncoderDecoder.OPCODES opCode = TftpEncoderDecoder.OPCODES.NONE;

    private static boolean fullMessageReceived = false;

    public static void main(String[] args) throws IOException
    {
        Thread userInputThread;
        Thread socketOutputThread;

        AtomicReference<String> userInput = new AtomicReference<>("");

        if (args.length == 0)
        {
            args = new String[]{"localhost", "7777"};
        }

        if (args.length < 2)
        {
            System.out.println("you must supply two arguments: host, port");
            System.exit(1);
        }

        try (Socket sock = new Socket(args[0], Integer.parseInt(args[1]));
             BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream()))
        {
            System.out.println("Connected to the server!");

            userInputThread = new Thread(() ->
            {
                Scanner scanner = new Scanner(System.in);

                while (!TftpClient.protocol.shouldTerminate())
                {
                    userInput.set(scanner.nextLine());
                    processCommand(userInput.get(), out);
                }
            }, "userInputThread");

            socketOutputThread = new Thread(() ->
            {
                while (!protocol.shouldTerminate())
                {
                    readSocketInput(in, out);
                }
            }, "socketOutputThread");

            userInputThread.start();
            socketOutputThread.start();

            try
            {
                userInputThread.join();
                socketOutputThread.join();
            }
            catch (InterruptedException ignored){}
        }
    }

    private static void processCommand(String str, BufferedOutputStream out)
    {
        String[] words = str.split("\\s+", 2);

        try
        {
            opCode = TftpEncoderDecoder.OPCODES.valueOf(words[0]);
        }
        catch (IllegalArgumentException ignored)
        {
            System.out.println("Invalid command");
            return;
        }

        if((opCode == TftpEncoderDecoder.OPCODES.LOGRQ ||
           opCode == TftpEncoderDecoder.OPCODES.RRQ    ||
           opCode == TftpEncoderDecoder.OPCODES.DELRQ  ||
           opCode == TftpEncoderDecoder.OPCODES.WRQ) && words.length == 1)
        {
            System.out.println("Missing command arg");
            return;
        }

        processMessage(words[0], words.length == 2 ? words[1] : null, out);
    }

    private static void readSocketInput(BufferedInputStream in, BufferedOutputStream out)
    {
        int read;

        try
        {
            while (!protocol.shouldTerminate() && (read = in.read()) >= 0)
            {
                byte[] nextMessage = encdec.decodeNextByte((byte) read);

                if (nextMessage != null)
                {
                    byte[] res = protocol.process(nextMessage);

                    if(res != null && res.length > 0)
                    {
                        sendBytes(res, out);
                    }

                    if(protocol.shouldWakeUp())
                    {
                        synchronized (lock)
                        {

                            fullMessageReceived = true;
                            protocol.setWakeUp(false);
                            opCode = TftpEncoderDecoder.OPCODES.NONE;
                            lock.notifyAll();
                        }
                    }
                }
            }
        }
        catch (IOException ignored){}
    }
    private static void processMessage(String command, String name, BufferedOutputStream out)
    {
        switch (command)
        {
            case "LOGRQ":
                sendLogin(name, out);
                break;
            case "RRQ":
                sendRead(name,out);
                break;
            case "WRQ":
                sendWrite(name,out);
                break;
            case "DELRQ":
                sendDelete(name,out);
                break;
            case "DIRQ":
                sendDirEnumerate(out);
                break;
            case "DISC":
                sendDisconnect(out);
                break;
            default:
                assert false;
        }
    }

    private static void sendLogin(String name, BufferedOutputStream out)
    {
        sendMessage(name, out);
    }

    private static void sendRead(String fileName, BufferedOutputStream out)
    {
        File newFile = new File(dirPath + "/" + fileName);

        if (newFile.exists())
        {
            System.out.println("file already exists");
            return;
        }

        try
        {
            if(newFile.createNewFile())
            {
                protocol.setFileName(fileName);
                sendMessage(fileName, out);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static void sendWrite(String fileName, BufferedOutputStream out)
    {
        String filePath = dirPath + "/" + fileName;
        File newFile = new File(filePath);

        if(!(newFile.exists() && newFile.canRead()))
        {
            System.out.println("file does not exists");
            return;
        }

        protocol.setFileName(filePath);
        sendMessage(fileName, out);
    }

    private static void sendDelete(String fileName, BufferedOutputStream out)
    {
            sendMessage(fileName, out);
    }

    private static void sendDirEnumerate(BufferedOutputStream out)
    {
        sendMessage(null, out);
    }

    private static void sendDisconnect(BufferedOutputStream out)
    {
        sendMessage(null, out);
    }

    private static void sendMessage(String name, BufferedOutputStream out)
    {
        synchronized (TftpClient.lock)
        {
            byte[] res = Utils.concatenateByteArrays(opCode.ToByteArray(), name != null ? name.getBytes() : new byte[]{});

            assert res.length > 0;
            sendBytes(res, out);


            while (!fullMessageReceived)
            {
                try
                {
                    TftpClient.lock.wait();
                }
                catch (InterruptedException ignored) {}
            }

            fullMessageReceived = false;
        }
    }

    private static void sendBytes(byte[] data, BufferedOutputStream out)
    {
        try
        {
            protocol.setCurrOpCode(opCode);
            out.write(encdec.encode(data));
            out.flush();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}

