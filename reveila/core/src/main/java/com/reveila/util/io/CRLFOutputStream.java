package com.reveila.util.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Charles Lee
 *
 * Convert lines into the canonical MIME format, that is,
 * terminate lines with CRLF.
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
