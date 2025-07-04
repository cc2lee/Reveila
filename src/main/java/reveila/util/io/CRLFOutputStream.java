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
 * Convert lines into the canonical MIME format, that is,
 * terminate lines with CRLF.<p>
 *
 * This stream can be used with the Part.writeTo and Message.writeTo
 * methods to generate the canonical MIME format of the data for the
 * purpose of (e.g.) sending it via SMTP or computing a digital
 * signature.
 */
public class CRLFOutputStream extends FilterOutputStream {
	
	protected int lastb = -1;
	protected static byte[] newline;
	
	static {
		newline = new byte[2];
		newline[0] = (byte)'\r';
		newline[1] = (byte)'\n';
	}
	
	public CRLFOutputStream(OutputStream os) {
		super(os);
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
		int start = off;
		len += off;
		for (int i = start; i < len ; i++) {
			if (b[i] == '\r') {
				out.write(b, start, i - start);
				out.write(newline);
				start = i + 1;
			}
			else if (b[i] == '\n') {
				if (lastb != '\r') {
					out.write(b, start, i - start);
					out.write(newline);
				}
				start = i + 1;
			}
			lastb = b[i];
		}
		if ((len - start) > 0)
			out.write(b, start, len - start);
	}

}
