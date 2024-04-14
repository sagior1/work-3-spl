package bgu.spl.net.srv;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T>{
    private ConcurrentHashMap<Integer, BlockingConnectionHandler<T>> clients = new ConcurrentHashMap<>();
    int counter = 0;
    private static ConnectionsImpl singleton = null;


void connect(int connectionId, ConnectionHandler<T> handler){
    clients.put(connectionId, (ConnectionHandler<T>) handler);
}

boolean send(int connectionId, T msg){
        try
        {
            clients.get(connectionId).send(msg); // To search using int value and not Integer Object
        }
        catch (Exception ignored)
        {
            return false;
        }

        return true;
}

void disconnect(int connectionId){
    clients.remove(connectionId);
}

public static synchronized Connections getSingleton()
{
        if (singleton == null){
            singleton = new ConnectionsImp();
        }

        return singleton;
}

// TODO: check if necessary 
public static int addClient()
{
    return counter++;
}

public BlockingConnectionHandler<T> getClient(Integer connectionId)
{
    return clients.get(connectionId);
}

public ConcurrentHashMap<Integer, BlockingConnectionHandler<T>> getclientsMap(){

     return clients;
}
}


