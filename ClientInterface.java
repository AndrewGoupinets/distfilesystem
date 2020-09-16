import java.rmi.*;

public interface ClientInterface extends Remote {
    // called back from the server to invalidate the current file cache
    public boolean invalidate( ) throws RemoteException;

    // called back from the server to write back the current file cache to server
    public boolean writeback( ) throws 	RemoteException;
}
