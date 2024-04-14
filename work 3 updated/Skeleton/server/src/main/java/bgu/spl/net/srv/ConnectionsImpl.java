package bgu.spl.net.srv;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T>
{
    private static ConnectionsImpl singleton = null;
    private final ConcurrentHashMap<Integer, BlockingConnectionHandler<T>> clientsMap = new ConcurrentHashMap<>();
    private static int uniqueIdCounter = 0;

    public void connect(int connectionId, ConnectionHandler<T> handler)
    {
        clientsMap.put(connectionId, (BlockingConnectionHandler<T>) handler);
    }

    public boolean send(int connectionId, T msg)
    {
        try
        {
            clientsMap.get((Integer) connectionId).send(msg); // To search using int value and not Integer Object
        }
        catch (Exception ignored)
        {
            return false;
        }

        return true;
    }

    public void disconnect(int connectionId)
    {
        try
        {
            clientsMap.get((Integer) connectionId).close();
            clientsMap.remove((Integer) connectionId);
        }
        catch (IOException ignored){}
    }


    public static synchronized Connections getSingleton()
    {
            if (singleton == null)
            {
                singleton = new ConnectionsImpl();
            }

            return singleton;
    }

    public BlockingConnectionHandler<T> getClient(Integer id)
    {
        return clientsMap.get(id);
    }

    public static int addNewClient()
    {
        return uniqueIdCounter++;
    }

    public ConcurrentHashMap<Integer, BlockingConnectionHandler<T>> getclientsMap()
    {
         return clientsMap;
    }
}
