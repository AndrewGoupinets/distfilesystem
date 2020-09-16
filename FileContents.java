import java.io.*;
import java.util.*;

public class FileContents implements Serializable {
    // actual file data
    private byte[] contents;

    // a client-site file cache is constructed with byte data.
    public FileContents( byte[] contents ) {
	this.contents = contents;
    }

    // print out a reference to the current file cache
    public void print( ) throws IOException {
	System.out.println( "FileContents = " + contents );
    }

    // retrieve the fie cache contents
    public byte[] get( ) {
	return contents;
    }
}
