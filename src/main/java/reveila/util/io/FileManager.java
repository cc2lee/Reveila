package reveila.util.io;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import reveila.util.GUID;

/**
 * @author Charles Lee
 *
 * This class provides the default implementation of the FileManager interface.
 */
public final class FileManager {

	private String fileHome;
	public String getFileHome() {
		return fileHome;
	}

	private String tempHome;
	public String getTempFileHome() {
		return tempHome;
	}
	
	/**
	 * Constructor, taking an abstract pathname <code>fileHome</code> as its
	 * data file home, and an abstract pathname <code>tempHome</code> as its
	 * temp file home. The data file home argument must be a valid file directory,
	 * while the temp file home may be assigned null, in which case a directory
	 * named "Temp" at the root of the data file home is assumed. If such directory
	 * doesn't exist, it will be created. However, if a String that is neither null
	 * nor empty is specified as the temp file home, it must be a valid file
	 * directory, otherwise an IllegalArgumentException will be thrown.
	 * 
	 * @param fileHome - The data file home.
	 * @param tempFileHome - The temp file home.
	 * @throws IOException - if the required directories are not found or can not be created.
	 */
	public FileManager(String fileHome, String tempFileHome) throws IOException {
		super();
		setFileHome(fileHome);
		setTempFileHome(tempFileHome);
	}
	
	private void setTempFileHome(String absolutePath) throws IOException {
		if (absolutePath == null || absolutePath.length() == 0) {
			throw new IllegalArgumentException(
					"The argument must not be null or empty");
		}

		if (!absolutePath.endsWith(File.separator)) {
			absolutePath += File.separator;
		}
		
		File file = new File(absolutePath);
		if ((!file.exists()) || (!file.isDirectory())) {
			if (!file.mkdir()) {
				throw new IOException(
						"Could not create directory: " + file.toString());
			}
		}

		this.tempHome = file.getAbsolutePath();
	}

	private void setFileHome(String absolutePath) throws IOException {
		if (absolutePath == null || absolutePath.length() == 0) {
			throw new IllegalArgumentException(
					"The argument must not be null or empty");
		}

		if (!absolutePath.endsWith(File.separator)) {
			absolutePath += File.separator;
		}
		
		File file = new File(absolutePath);
		if ((!file.exists()) || (!file.isDirectory())) {
			if (!file.mkdir()) {
				throw new IOException(
						"Could not create directory: " + file.toString());
			}
		}
		
		this.fileHome = file.getAbsolutePath();
	}
	
	private void validate(File file) throws IllegalArgumentException {
		if (file == null) {
			throw new IllegalArgumentException("Null file object");
		}
		else if (file.isAbsolute()) {
			throw new IllegalArgumentException("Absolute path not permitted");
		}
	}
	
	//
	// Implementing the FileManager interface:
	//
	
	/**
	 * Creates a new file in the default file store allocated by the system.
	 * @see reveila.util.io.FileManager#createFile(java.io.File)
	 */
	public File createFile(File pathname) throws IOException {
		validate(pathname);
		File file = new File(fileHome, pathname.getPath());
		if (file.createNewFile()) {
			return file;
		} else {
			return null;
		}
	}
	/**
	 * Creates an empty file in the default temporary-file directory, using the
	 * given prefix and suffix to generate its name.
	 * @param prefix - The prefix string to be used in generating the file's name;
	 * must be at least three characters long.
	 * @param suffix - The suffix string to be used in generating the file's name;
	 * may be null, in which case the suffix ".tmp" will be used.
	 * @return An abstract pathname denoting a newly-created empty file.
	 * @throws IOException - If a file could not be created.
	 */
	public File createTempFile(String prefix, String suffix) throws IOException {
		return File.createTempFile(prefix, suffix, new File(tempHome));
	}
	/**
	 * Creates a file whose filename is globally unique, using the given prefix and extenstion.
	 * @see reveila.util.io.FileManager#createUniqueFile(java.lang.String, java.lang.String)
	 */
	public File createUniqueFile(String prefix, String extension) throws IOException {
		String guid = GUID.getGUID(new Object());
		String filename;
		if (prefix == null) {
			filename = guid;
		} else {
			filename = prefix + guid;
		}
		
		if (extension != null) {
			filename += "." + extension;
		}
		
		File file = new File(fileHome, filename);
		if (file.createNewFile()) {
			return file;
		} else {
			return null;
		}
	}
	/**
	 * Tests if the given relative pathname exists.
	 * @see reveila.util.io.FileManager#exists(java.io.File)
	 */
	public boolean exists(File pathname) throws IOException {
		validate(pathname);
		return (new File(fileHome, pathname.getPath())).exists();
	}
	/**
	 * Tests if the given relative pathname denotes a directory.
	 * @see reveila.util.io.FileManager#isDirectory(java.io.File)
	 */
	public boolean isDirectory(File pathname) throws IOException {
		validate(pathname);
		return (new File(fileHome, pathname.getPath())).isDirectory();
	}
	/**
	 * Tests if the given relative pathname denotes a normal file.
	 * @see reveila.util.io.FileManager#isFile(java.io.File)
	 */
	public boolean isFile(File pathname) throws IOException {
		validate(pathname);
		return (new File(fileHome, pathname.getPath())).isFile();
	}
	/**
	 * Resolves the given relative pathname to an absolute pathname, or null if no such file exists.
	 * @see reveila.util.io.FileManager#resolve(java.io.File)
	 */
	public File resolve(File pathname) throws IOException {
		validate(pathname);
		File f = new File(fileHome, pathname.getPath());
		if (f.exists()) {
			return f;
		} else {
			return null;
		}
	}
	/**
	 * Returns all the files in the directory <code>dir</code>, which satisfy the conditions
	 * of the the filename filter <code>filter</code>, as an array of java.io.File.
	 * @see reveila.util.io.FileManager#listFiles(java.io.File, java.io.FilenameFilter)
	 */
	public File[] listFiles(File dir, FilenameFilter filter) throws IOException {
		validate(dir);
		return (new File(fileHome, dir.getPath())).listFiles(filter);
	}
	/**
	 * Creates a single directory denoted by the abstract relative <code>pathname</code>.
	 * @see reveila.util.io.FileManager#makeDir(java.io.File)
	 */
	public File makeDir(File pathname) throws IOException {
		validate(pathname);
		File f = new File(fileHome, pathname.getPath());
		if (f.mkdir()) {
			return f;
		} else {
			return null;
		}
	}
	/**
	 * Creates the directory named by the given abstract relative pathname,
	 * including any necessary but nonexistent parent directories.
	 * @see reveila.util.io.FileManager#makeDirs(java.io.File)
	 */
	public File makeDirs(File pathname) throws IOException {
		validate(pathname);
		File f = new File(fileHome, pathname.getPath());
		if (f.mkdirs()) {
			return f;
		} else {
			return null;
		}
	}
	/**
	 * Deletes the file or directory denoted by the given abstract relative pathname.
	 * @see reveila.util.io.FileManager#delete(java.io.File)
	 */
	public boolean delete(File pathname) throws IOException {
		validate(pathname);
		return pathname.delete();
	}
}
