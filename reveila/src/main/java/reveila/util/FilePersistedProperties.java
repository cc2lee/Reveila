/*
 * Created on May 7, 2003
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package reveila.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Properties;

/**
 * @author Charles Lee
 */
public class FilePersistedProperties extends Properties {
	
	protected String filepath;
	
	public FilePersistedProperties() {
		super();
	}
	
	public FilePersistedProperties(String filepath) throws IOException {
		super();
		load(filepath);
	}
	
	public void save() throws IOException {
		if (filepath == null) {
			throw new IllegalStateException("null file path");
		}
		
		// Use try-with-resources to ensure the writer is always closed.
		try (Writer writer = new FileWriter(filepath)) {
			super.store(writer, "FilePersistedProperties saved by FilePersistedProperties.save()");
		}
	}
	
	public void load(String filepath) throws IOException {
		// Use try-with-resources for automatic stream closing
		try (InputStream ins = new BufferedInputStream(new FileInputStream(filepath))) {
			super.load(ins);
		}
		this.filepath = filepath;
	}

	/**
	 * @return
	 */
	public String getFilepath() {
		return filepath;
	}

	/**
	 * @param path
	 */
	public String setFilepath(String path) {
		String oldPath = this.filepath;
		this.filepath = path;
		return oldPath;
	}
	
	public String setFilepath(String path, boolean create) throws IOException {
		String oldPath = setFilepath(path);
		if (create) {
			File file = new File(path);
			if (!file.exists()) {
				// Ensure parent directories exist before creating the file.
				File parentDir = file.getParentFile();
				if (parentDir != null) {
					parentDir.mkdirs(); // Or Files.createDirectories(parentDir.toPath());
				}
				file.createNewFile(); // Or Files.createFile(file.toPath());
			}
		}
		return oldPath;
	}

}
