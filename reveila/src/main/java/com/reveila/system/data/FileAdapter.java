package com.reveila.system.data;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import com.reveila.system.PlatformAdapter;
import com.reveila.system.Proxy;

/**
 * @author Charles Lee
*/
public class FileAdapter {

	public static final int TASK_FILE = 3;
    public static final int CONF_FILE = 2;
    public static final int DATA_FILE = 1;
    public static final int TEMP_FILE = 0;

	private PlatformAdapter platformAdapter;
	private String dirName;
    
	public FileAdapter(PlatformAdapter platformAdapter, Proxy proxy) {
		super();
		Proxy p = Objects.requireNonNull(proxy, "Argument 'proxy' must not be null");
		this.dirName = p.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
		this.platformAdapter = Objects.requireNonNull(platformAdapter, "Argument 'PlatformAdapter' must not be null");
	}

	public InputStream getInputStream(int fileType, String relativePath) throws IOException {
		return this.platformAdapter.getFileInputStream(dirName + File.separator + removeLeadingFileSeparator(relativePath));
	}

	public OutputStream getOutputStream(int fileType, String relativePath) throws IOException {
		return this.platformAdapter.getFileOutputStream(dirName + File.separator + removeLeadingFileSeparator(relativePath));
	}

	private String removeLeadingFileSeparator(String path) {
		if (path == null || path.isEmpty() || !path.startsWith(File.separator)) { return path; }
		return path.substring(path.indexOf(File.separator) + 1);
	}

}