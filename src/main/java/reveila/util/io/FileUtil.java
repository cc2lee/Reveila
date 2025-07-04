package reveila.util.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * @author Charles Lee
 * 
 * This class provides utility methods for manipulating files.
 */
public final class FileUtil {

	public static String createDirectory(String absolutePath) throws IOException {
		if (absolutePath == null || absolutePath.length() == 0) {
			absolutePath = absolutePath == null ? "null" : "empty string";
			throw new IllegalArgumentException(
					"Malformed file path argument: " + absolutePath);
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

		return file.getAbsolutePath();
	}

	/**
	 * Deletes a normal file or directory denoted by the <code>file</code> argument.
	 * @param file - file or directory to delete.
	 * @return number of files deleted.
	 * @throws FileDeleteException if the operation cannot be full filled.
	 */
	public static final int delete(File file, boolean isDeleteEmptyFolder) throws FileDeleteException {
		if (file == null) {
			throw new IllegalArgumentException("null");
		} else if (!file.exists()) {
			throw new FileDeleteException("File not found: " + file.toString());
		}
		
		FileDeleteException delException = new FileDeleteException("File(s) could not be deleted");
		int delCount = 0;
		File[] files = null;
	
		if (file.isDirectory() && (files = file.listFiles()).length > 0) {
			for (int i = 0; i < files.length; i++) {
				try {
					delCount += delete(files[i], isDeleteEmptyFolder);
				}
				catch (FileDeleteException e) {
					List<File> fails = e.getFailedFiles();
					Iterator<File> itr = fails.iterator();
					while (itr.hasNext()) {
						File f = (File) itr.next();
						delException.getFailedFiles().add(f);
					}
				}
			}

			if (isDeleteEmptyFolder) {
				if (file.delete()) {
					delCount++;
				} else {
					file.deleteOnExit();
					delException.getFailedFiles().add(file);
				}
			}
		} else { // it's a file (not directory)
			if (file.delete()) {
				delCount++;
			} else {
				// these deletes failed with unknown causes
				// we request that the file be deleted upon virtual machine terminates
				file.deleteOnExit();
				delException.getFailedFiles().add(file);
			}
		}
		
		if (delException.getFailedFiles().size() > 0) {
			throw delException;
		}
		
		return delCount;
	}
	
	/**
	 * Checks if the specified file is being serialized.
	 * The degree of accuracy is relative to the second argument <code>waitMini</code>,
	 * the bigger the value, the more accurate the result.
	 * @param file - the file to be checked.
	 * @param waitMini - time in mini seconds between comparison.
	 * @return true if the file length is not changing after the specified wait time, otherwise, false.
	 * @throws InterruptedException if the wait had been interrupted.
	 */
	public static boolean isSerializing(File file, int waitMini) throws InterruptedException {
		long length = file.length();
		Thread.sleep(waitMini);
		return length != file.length();
	}
	
	/**
	 * Returns the filename base and filename extension as a String array, omitting the last "." separator.
	 * @param filename - the filename to parse.
	 * @param toLowerCase - If true, the string tokens will be converted to lower cases before they are returned.
	 * @return the filename base and filename extension as a String array.
	 */
	public static String[] getNameBaseAndExtension(String filename, boolean toLowerCase) {
		if (toLowerCase) {
			filename = filename.toLowerCase();
		}
		String[] tokens = {filename, ""};
		int index = filename.lastIndexOf('.');
		if (index > -1) {
			tokens[0] = filename.substring(0, index);
			if (index + 1 < filename.length()) {
				tokens[1] = filename.substring(index + 1);
			}
		}
		return tokens;
	}
	
	/**
	 * Copies the source file to the destination <code>target</code>.
	 * If the destination <code>target</code> does not exist, it will be created as a normal file.
	 * If the destination <code>target</code> exists and is a directory, a new file with the same
	 * name as the source file will be created in the destination directory as the result file.
	 * If the destination <code>target</code> exists and is a normal file, it will be overwritten
	 * if the argument <code>overwrite</code> is true, otherwise an IO exception will be thrown.
	 * 
	 * @param source - the source file to copy.
	 * @param target - the destination file or directory.
	 * @param bufferSize - file copy buffer size, 0 for no buffering.
	 * @param overwrite - If true, overwrite the destination file if it exists, otherwise throws IO exception.
	 * @return the length of the file copied in bytes.
	 * @throws IOException if the copy operation fails.
	 */
	public static long copyFile(File source, File target, int bufferSize, boolean overwrite) throws IOException {
		
		if (source == null) {
			throw new IOException("Source file specified is null");
		}
		else if (!source.exists()) {
			throw new IOException("Source file specified could not be found: " + source.getAbsolutePath());
		} 
		else if (!source.isFile()) {
			throw new IOException("Source file specified does not denote a normal file: " + source.getAbsolutePath());
		}
		
		File dFile = null;
		if (target == null) {
			throw new IOException("Destination file specified is null");
		}
		else if (target.exists()) {
			if (target.isDirectory()) {
				dFile = new File(target, source.getName());
			}
			else if (!overwrite) {
				throw new IOException("Destination file already exists: " + target.getAbsolutePath());
			}
			
		}
		else {
			dFile = target;
		}
		
		if (bufferSize < 1) {
			bufferSize = 1;
		}
		
		BufferedInputStream bis = null;
		BufferedOutputStream bos = null;
		long length = 0;
		
		try {
			bis = new BufferedInputStream(new FileInputStream(source), bufferSize);
			bos = new BufferedOutputStream(new FileOutputStream(dFile), bufferSize);
			byte[] byteBuf = new byte[bufferSize];
			int read = 0;
			while ((read = bis.read(byteBuf, 0, bufferSize)) != -1) {
				bos.write(byteBuf, 0, read);
				length += read;
			}
			bos.flush();
		} catch (IOException e) {
			throw e;
		} finally {
			if (bis != null) {
				try {
					bis.close();
				} catch (Exception e) {}
			}
			if (bos != null) {
				try {
					bos.close();
				} catch (Exception e) {}
			}
		}
		
		return length;
	}
	
	/**
	 * Copies a file directory, recursively, to the target directory.
	 * If the target directory does not exist yet, it will be created, and the files including sub-directories
	 * from the source directory will be copied into the newly created destination directory.
	 * If the target directory already exists, a new directory using the same name as the source directory
	 * will be created there, and the files including sub-directories from the source directory will be copied
	 * into this newly created destination directory.
	 * 
	 * @param source - the source directory.
	 * @param target - the target directory.
	 * @param bufferSize - buffer size for file copy, 0 for no buffering.
	 * @param overwrite - if true, existing file will be overwritten, otherwise an IO exception will be thrown if the destination file already exists.
	 * @return - the total bytes copies.
	 * @throws IOException if the operation fails.
	 */
	public static long copyDir(File source, File target, int bufferSize, boolean overwrite) throws IOException {
		
		if (source == null) {
			throw new IOException("Source directory specified is null");
		}
		else if (!source.exists()) {
			throw new IOException("Source directory specified could not be found: " + source.getAbsolutePath());
		} 
		else if (!source.isDirectory()) {
			throw new IOException("Source specified does not denote a directory: " + source.getAbsolutePath());
		}
		
		File dDir = null;
		if (target == null) {
			throw new IOException("Destination directory specified is null");
		}
		else if (target.exists()) {
			if (target.isDirectory()) {
				dDir = new File(target, source.getName());
				if (!dDir.mkdir()) {
					throw new IOException("Could not create destination directory: " + dDir.getAbsolutePath());
				}
			}
			else {
				throw new IOException("Destination specified does not denote a directory: " + target.getAbsolutePath());
			}
			
		}
		else {
			if (target.mkdirs()) {
				dDir = target;
			} else {
				throw new IOException("Could not create destination directory: " + target.getAbsolutePath());
			}
		}
		
		File[] srcFiles = source.listFiles();
		if (srcFiles == null) {
			throw new IOException("The source directory is not accessible: " + source.getAbsolutePath());
		}
		
		if (bufferSize < 1) {
			bufferSize = 1;
		}
		
		long bytes = 0;
		
		for (int i = 0; i < srcFiles.length; i++) {
			File src = srcFiles[i];
			if (src.isFile()) {
				bytes += copyFile(src, dDir, bufferSize, overwrite);
			} else {
				bytes += copyDir(src, dDir, bufferSize, overwrite);
			}
		}
		
		return bytes;
	}

	public static File[] listFilesWithExtension(String dirPath, String ext) {

        File dir = new File(dirPath);
        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith("." + ext);
            }
        };
        return dir.listFiles(filter);
        
    }

}
