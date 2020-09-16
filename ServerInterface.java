import java.rmi.*;

public interface ServerInterface extends Remote {
    // allows a client to download a givnen file with mode [r/w].
    public FileContents download( String client, String filename, String mode )
	throws RemoteException;

    // allows a client to upload her/his modified file back to the server.
    public boolean upload( String client, String filename, 
			   FileContents contents ) throws RemoteException;
}
