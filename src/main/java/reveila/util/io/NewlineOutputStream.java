/*
 * Created on Nov 11, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package reveila.util.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Charles Lee
 *
 * Convert the various newline conventions to the local platform's
 * newline convention.<p>
 *
 * This stream can be used with the Message.writeTo method to
 * generate a message that uses the local plaform's line terminator
 * for the purpose of (e.g.) saving the message to a local file.
 */
public class NewlineOutputStream extends FilterOutputStream {
	
    private int lastb = -1;
    private static byte[] newline;

    public NewlineOutputStream(OutputStream os) {
		super(os);
		if (newline == null) {
		    String s = System.getProperty("line.separator");
		    if (s == null || s.length() <= 0)
				s = "\n";
		    newline = s.getBytes();
		}
    }

    public void write(int b) throws IOException {
		if (b == '\r') {
		    out.write(newline);
		} else if (b == '\n') {
		    if (lastb != '\r')
			out.write(newline);
		} else {
		    out.write(b);
		}
		lastb = b;
    }

    public void write(byte b[]) throws IOException {
		write(b, 0, b.length);
    }

    public void write(byte b[], int off, int len) throws IOException {
		for (int i = 0 ; i < len ; i++) {
		    write(b[off + i]);
		}
    }

}
