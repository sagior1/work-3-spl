package bgu.spl.net.srv;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T>
{
    private static int counter = 0;
    private static ConnectionsImpl singleton = null;
    private ConcurrentHashMap<Integer, BlockingConnectionHandler<T>> clients = new ConcurrentHashMap<>();
    

    public void connect(int connectionId, ConnectionHandler<T> handler)
    {
        clients.put(connectionId, (BlockingConnectionHandler<T>) handler);
    }

    public boolean send(int connectionId, T msg)
    {
        try
        {
            clients.get(connectionId).send(msg); 
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
            clients.get(connectionId).close();
            clients.remove(connectionId);
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
        return clients.get(id);
    }

    public static int addClient()
    {
        return counter++;
    }

    public ConcurrentHashMap<Integer, BlockingConnectionHandler<T>> getclientsMap()
    {
         return clients;
    }
}
