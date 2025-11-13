package reveila.error;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Charles Lee
 */
public class FileDeleteException extends IOException {

	private List<File> failedFiles = new LinkedList<File>();
	
	/**
	 * Constructor.
	 */
	public FileDeleteException() {
		super();
	}

	/**
	 * Constructor.
	 * @param message - detailed message.
	 */
	public FileDeleteException(String message) {
		super(message);
	}

	/**
	 * Constructor.
	 * @param message - detailed message.
	 * @param failedFiles - files that could not be deleted.
	 */
	public FileDeleteException(String message, File[] failedFiles) {
		super(message);
		if (failedFiles != null) {
			for (int i = 0; i < failedFiles.length; i++) {
				this.failedFiles.add(failedFiles[i]);
			}
		}
	}

	/**
	 * Returns the failed files as a List object.
	 * @return the failed files as a List object.
	 */
	public List<File> getFailedFiles() {
		return failedFiles;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		StringBuffer strBuf = new StringBuffer();
		strBuf.append(getClass().getName()).append(": ");
		strBuf.append(getMessage());
		
		if (failedFiles.size() > 0) {
			Iterator<File> i = failedFiles.iterator();
			File file = (File) i.next();
			strBuf.append("; ");
			strBuf.append(file.getAbsolutePath());
			while (i.hasNext()) {
				file = (File) i.next();
				strBuf.append(", ");
				strBuf.append(file.getAbsolutePath());
			}
		}
		
		return strBuf.toString();
	}

}
