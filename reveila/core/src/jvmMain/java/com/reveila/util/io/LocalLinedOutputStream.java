package com.reveila.util.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Charles Lee
 *
 *         Convert the various newline conventions to the local platform's
 *         newline convention.
 */
public class LocalLinedOutputStream extends FilterOutputStream {

	private int lastb = -1;
	private byte[] newline;

	public LocalLinedOutputStream(OutputStream os) {
		super(os);
		if (newline == null) {
			String s = System.getProperty("line.separator");
			if (s == null || s.isEmpty())
				s = "\n";
			newline = s.getBytes();
		}
	}

	@Override
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

	@Override
	public void write(byte b[]) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte b[], int off, int len) throws IOException {
		for (int i = 0; i < len; i++) {
			write(b[off + i]);
		}
	}

}
