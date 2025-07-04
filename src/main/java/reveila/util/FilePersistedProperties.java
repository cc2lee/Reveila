/*
 * Created on May 7, 2003
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package reveila.util;

import java.io.*;
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
	
	public void save() throws FileNotFoundException {
		if (filepath == null) {
			throw new IllegalStateException("null file path");
		}
		
		PrintWriter outs = new PrintWriter(new FileOutputStream(this.filepath));
		super.list(outs);
		
		try {
			outs.flush();
			outs.close();
		} catch (Exception e) {}
	}
	
	public void load(String filepath) throws IOException {
		BufferedInputStream ins = new BufferedInputStream(new FileInputStream(filepath));
		super.load(ins);
		this.filepath = filepath;
		try {
			ins.close();
		} catch (Exception e) {}
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
			if (!file.exists() || !file.isFile()) {
				file.createNewFile();
			}
		}
		return oldPath;
	}

}
