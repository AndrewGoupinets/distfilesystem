import java.io.*;                  // IOException
import java.net.*;                 // InetAddress
import java.rmi.*;                 // Naming
import java.rmi.server.*;          // UnicastRemoteObject
import java.rmi.registry.*;        // rmiregistry

/**
 * Is a DFS client program that downloads and uploads a file requested by the
 * client. Its implementation is based on the session semantics and the write
 * invalidation.
 */
public class FileClient extends UnicastRemoteObject 
    implements ClientInterface {

    private BufferedReader input = null;    // standard input
    private final static String ramDiskFile // a cached file in /tmp: CHANGE!!
	= "/tmp/munehiro.txt";
    private ServerInterface server = null;  // a DFS server interface
    private File file = null;               // a cached file in memory

    private boolean emacs_option = false;   // invoke emacs if true, otherwise vim

    /** 
     * Is a constructor that connects to a given DFS server, creates a cache
     * file entry and sets up the standard input.
     *
     * @param machinename the server's ip name
     * @param port        the server's port
     */
    public FileClient( String machinename, String port, boolean emacs_option ) 
	throws RemoteException {

	// server search
	try {
	    server = ( ServerInterface )Naming.lookup( "rmi://" + machinename +
						       ":" + port + 
						       "/fileserver" );
	} catch ( Exception e ) {
	    e.printStackTrace( );
	}

	// file cache creation;
	file = new File( );

	// Standard input
	input = new BufferedReader( new InputStreamReader( System.in ) );

	// Enable emacs or not. If not, vim is invoked
	this.emacs_option = emacs_option;
    }

    /**
     * Is the main loop where the client program reads a file name and its
     * acces mode from a client, downloads the file if it is not cached, and
     * finally opens it with emacs or vim.
     */
    public void loop( ) {
	// go into the main loop
	while( true ) {
	    // If a client doesn't type any inputs immediately, FileClient's main
	    // program won't do anything immediately. Therefore, let's start
	    // a writeback thread.

	    // This thread works in background so that it will uploads
	    // the cached file to the server as soon as it calls writeback( ).
	    WritebackThread writebackThread = new WritebackThread( );
	    writebackThread.start( );

	    // user input: file name and open mode
	    String filename = null;
	    String mode = null;
	    try {
		System.out.println( "FileClient: Next file to open:" );
		System.out.print( "\tFile name: " );
		filename = input.readLine( );
		if ( filename.equals( "quit" ) || filename.equals( "exit" ) ) {
		    if ( file.isStateWriteOwned( ) )
			file.upload( );
		    System.exit( 0 );
		} else if ( filename.equals( "" ) ) {
		    System.err.println( "Do it again" );
		    writebackThread.kill( );
		    continue;
		}
		
		System.out.print( "\tHow(r/w): " );
		mode = input.readLine( );
		if ( !mode.equals( "r" ) && !mode.equals( "w" ) ) {
		    System.err.println( "Do it again" );
		    writebackThread.kill( );
		    continue;
		}
		     
	    } catch ( IOException e ) {
		e.printStackTrace( );
	    }

	    // Now, the main thread manipulates the cached file. It terminates
	    // the background thread that takes care of uploading the cached
	    // file to the server.
	    writebackThread.kill( );
	    
	    // look through the cache
	    if ( file.hit( filename, mode ) != true ) {
		// cache miss
		if ( file.isStateWriteOwned( ) ) {
		    // replacement
		    file.upload( );
		}
		// download a file from the server
		if ( file.download( filename, mode ) == false ) {
		    System.out.println( "File downloaded failed" );
		    continue;
		}
	    }
	    // open an editor
	    file.launchEditor( mode );
	}
    }

    /**
     * Is an RMI function called by the DFS server to invalidate the current
     * cached file.
     *
     * @return true if invalidation takes place in success
     */
    public boolean invalidate( ) {
	return file.invalidate( );
    }

    /**
     * Is an RMI function called by the DFS server to write back the current
     * cached file.
     *
     * @return true if writeback is scheduled in success
     */
    public boolean writeback( ) {
	return file.writeback( );
    }

    /**
     * Is the main function that receives a DFS server's ip name and port#.
     * It then instantiates a DFS client object and calls the loop( )
     * function where it repeats downloading and uploading a file requested
     * by the client user.
     * @param args the array that includes server_ip in its 0th element,
     *             the server port in the 1st element, and the e option
     *             in the 2nd element. If e is added, emacs is poppoed out
     *             for editing a file. Otherwise, vim is invoked.
     */
    public static void main( String[] args ) {
	// checking arguments
	if ( args.length != 2 && args.length != 3 ) {
	    System.err.println( "usage: java FileClient server_ip port# [e]" );
	    System.exit( -1 );
	}

	try {
	    // create a client object
	    FileClient client 
		= new FileClient( args[0], args[1], 
				  ( args.length == 3 && args[2].equals( "e" ) )
				  );
	    // registering myself
	    startRegistry( Integer.parseInt( args[1] ) );
	    Naming.rebind( "rmi://localhost:" + args[1] + "/fileclient", 
			   client );
	    System.out.println( "rmi://localhost: " + args[1] + "/fileclient" +
				" invokded" );

	    // goes into the main loop of file operations
	    client.loop( );
	} catch ( Exception e ) {
	    e.printStackTrace( );
	    System.exit( 1 );
	}
    }

    /**
     * Starts an RMI registry in background, which relieves a user from
     * manually starting the registry and thus prevents her/him from
     * forgetting its termination upon a logout.
     */
    private static void startRegistry( int port ) throws RemoteException {
        try {
            Registry registry =
                LocateRegistry.getRegistry( port );
            registry.list( );
        }
        catch ( RemoteException e ) {
            Registry registry =
                LocateRegistry.createRegistry( port );
        }
    }

    /**
     * Is a background thread that uploads the cached file back to the DFS
     * server in response to its writeback request.
     */
    private class WritebackThread extends Thread {
	private boolean active = false;  // reset when the main thread kills me

	public WritebackThread( ) {      // the constructor simply sets the
	    active = true;               // active variable 
	}

	// If the DFS server calls writeback( ) that changes the file state to
	// state_back2readshared, I will write back the cached file to the 
	// server.
	public void run( ) {
	    while ( isActive( ) ) {
		if ( file.isStateBackToReadShared( ) )
		    file.upload( );
	    }
	}
	// This function is called by the main thread when it is about to
	// manipulate the cached file with emacs/vim. I must be terminated.
	synchronized void kill ( ) {
	    active = false;
	    try {
		this.join( );
	    } catch ( InterruptedException e ) {
		e.printStackTrace( );
	    }
	}
	// Is called by run( ) to check if I can be still alive.
	synchronized boolean isActive( ) {
	    return active;
	}
    }

    /**
     * Is FileClient's private File class. The file client has only one file
     * cache. Therefore this class instantiates only one object. It has all
     * necessary file caching logics:
     *     invalidate( ), writeback( ), hit( ), download( ), upload( ),
     *     and launchEditor( )
     */
    private class File {
	private final static int state_invalid = 0;
	private final static int state_readshared = 1;
	private final static int state_writeowned = 2;
	private final static int state_back2readshared = 3;
	private int state = state_invalid;
	private boolean inEdit = false;
	private String name = "";
	private boolean ownership = false;
	private byte[] bytes = null;
	private String myIpName = null;
	public File( ) {
	    // My IP name
	    try {
		InetAddress inetaddr = InetAddress.getLocalHost( );
		myIpName = inetaddr.getHostName( );
	    } catch ( java.net.UnknownHostException e ) {
		e.printStackTrace( );
	    }
	}
	public synchronized boolean isStateInvalid( ) {
	    return ( state == state_invalid );
	}
	public synchronized boolean isStateReadShared( ) {
	    return ( state == state_readshared );
	}
	public synchronized boolean isStateWriteOwned( ) {
	    System.out.println( "name = "  + name +
				" state = " + state +
				" ownership = " + ownership );
	    return ( state == state_writeowned );
	}
	public synchronized boolean isStateBackToReadShared( ) {
	    return ( state == state_back2readshared );
	}

	/**
	 * If the cached file is in readshared, it will be invalidated.
	 * Otherwise, the file is owned and thus can't be invalidated.
	 *
	 * @return true if the current file cache can be invalidated
	 */
	public synchronized boolean invalidate( ) {
	    if ( state == state_readshared ) {
		state = state_invalid;
		System.out.println( "file( " + name + ") invalidated...state "
				    + state );
		return true;
	    } else
		return false;
	}

	/**
	 * If the cached file is in writeowned, it will transit to
	 * readshared, so that the cached file can be uploaded to the 
	 * server later.
	 *
	 * @return true if the current file cache can be uploaded later.
	 */
	public synchronized boolean writeback( ) {
	    if ( state == state_writeowned ) {
		state = state_back2readshared;
		return true;
	    } else
		return false;
	}

	/**
	 * Checks if a given file is currently cached with a given mode (r/w).
	 *
	 * @return true if a given file is currently cached with a given mode
	 *         r or w.
	 */
	public synchronized boolean hit( String name, String mode ) {
	    if ( !this.name.equals( name ) ) {
		System.out.println( "file: " + name + " does not exist." );
		return false;
	    }
	    if ( state == state_back2readshared ) {
		System.out.println("file: " + name + " must be written back.");
		return false;
	    }
	    if ( mode.equals( "r" ) && state != state_invalid ) {
		System.out.println( "file: " + name + " exists for read." );
		return true;
	    }
	    if ( mode.equals( "w" ) && state == state_writeowned ) {
		System.out.println( "file: " + name + " is owned for write" );
		return true;
	    }
	    System.out.println( "file: " + name + " accessed with " + mode );
	    return false;
	}

	/**
	 * Downloads file contents of a given filename from the server with
	 * mode (r/w).
	 * @param filename a file name the cleint wants to download
	 * @param mode "r" for read or "w" fro write
	 *
	 * @return true if a file download completes in success.
	 */
	public boolean download( String filename, String mode ) {
	    System.out.println( "downloading: " + filename + " with " +
				mode + " mode" );

	    // state transition
	    synchronized( this ) {
		switch( state ) {
		case state_invalid:
		    if ( mode.equals( "r" ) )
			state = state_readshared;
		    else if ( mode.equals( "w" ) ) {
			state = state_writeowned;
		    }
		    break;
		case state_readshared:
		    if ( mode.equals( "w" ) )
			 state = state_writeowned;
		    break;
		}
	    }

	    // download file contents from the server
	    name = filename;
	    ownership = mode.equals( "w" );
	    try {
		FileContents contents = server.download( myIpName,
							 name, mode );
		bytes = contents.get( );
	    } catch ( RemoteException e ) {
		e.printStackTrace( );
		return false;
	    } catch ( NullPointerException e ) {
		return false;
	    }
	    return true;
	}

	/**
	 * Uploads cached file contnets.
	 *
	 * @return true if a file upload completes in success.
	 */
	public boolean upload( ) {
	    System.out.println( "uploading: " + name + " start" );

	    // state transition
	    synchronized( this ) {
		switch( state ) {
		case state_writeowned: 
		    state = state_invalid;
		    break;
		case state_back2readshared:
		    state = state_readshared;
		    break;
		}
	    }

	    // upload file contents to the server
	    FileContents contents = new FileContents( bytes );
	    try {
		server.upload( myIpName, name, contents );
	    } catch ( RemoteException e ) {
		e.printStackTrace( );
		return false;
	    }
	    System.out.println( "uploading: " + name + " completed" );
	    return true;
	}

	/**
	 * Is called internally from launchEditor to execute a Unix command
	 * such as "chmod", "emacs", or "vim".
	 *
	 * @param  cmd a unix command such as "chmod", "emacs", or "vim".
	 * @param  arg1 the 1st argument passed to this Unix command. It must
	 *              not be null
	 * @param  arg2 the 2nd argument passed to this Unix command. It must
	 *              not be null. If arg2 is not necessary, it must be ""
	 * @return true if a given command has been successfully invoked.
	 */
	private boolean execUnixCommand(String cmd, String arg1, String arg2) {
	    // create a string array that include a command and its arguments
	    String[] cmdarray 
		= arg2.equals( "" ) ?  new String[2] : new String[3];
	    cmdarray[0] = cmd;
	    cmdarray[1] = arg1;
	    if ( !arg2.equals( "" ) ) 
		cmdarray[2] = arg2;
	    try {
		// Process builder with inherited IO should allow a console
		// based editor to take over the terminal.
		ProcessBuilder pb = new ProcessBuilder();
		pb.command( cmdarray );
		pb.redirectInput( ProcessBuilder.Redirect.INHERIT );
		pb.redirectOutput( ProcessBuilder.Redirect.INHERIT );
		pb.redirectError( ProcessBuilder.Redirect.INHERIT) ;
		pb.start( ).waitFor( );   // invoke a Unix cmd and wait for it


	    } catch ( IOException e ) {
		e.printStackTrace( );
		return false;
	    } catch ( InterruptedException e ) {
		e.printStackTrace( );
		return false;
	    }
	    return true;
	}

	/**
	 * Is called by the main thread's loop( ) function. This function
	 * changes the file mode appropriately with chmod and thereafter
	 * invokes emacs or vim.
	 *
	 * @param mode the file access mode: "r" or "w"
	 * @return true if emacs/vim has been successuflly invoked and finished.
	 */
	public boolean launchEditor( String mode ) {

	    // chmod 600
	    if ( execUnixCommand( "chmod", "600", ramDiskFile ) == false )
		return false;

	    // write the cached file contents to ramDiskFile /tmp/mfukuda.txt
	    try {
		FileOutputStream file = new FileOutputStream( ramDiskFile );
		file.write( bytes );
		file.flush( );
		file.close( );
	    } catch ( FileNotFoundException e ) {
		e.printStackTrace( );
		return false;
	    } catch ( IOException e ) {
		e.printStackTrace( );
		return false;
	    } 

	    // chmod 400 or 600
	    if ( execUnixCommand( "chmod", mode.equals( "r" ) ? "400" : "600",
				  ramDiskFile ) == false )
		return false;

	    // launch emacs or vim
	    boolean execCode = false;
	    if ( emacs_option )
		execCode = execUnixCommand( "emacs", ramDiskFile, "" );
	    else
		execCode = execUnixCommand( "vim", ramDiskFile, "" );

	    // read the updated file contents from ramDiskFile if "w" mode
	    if ( execCode == true && mode.equals( "w" ) ) {
		try {
		    FileInputStream file = new FileInputStream( ramDiskFile );
		    bytes = new byte[file.available( )];
		    file.read( bytes );
		    file.close( );
		} catch ( FileNotFoundException e ) {
		    e.printStackTrace( );
		    return false;
		} catch ( IOException e ) {
		    e.printStackTrace( );
		    return false;
		} 
	    }
	    return true;
	}
    }
}
