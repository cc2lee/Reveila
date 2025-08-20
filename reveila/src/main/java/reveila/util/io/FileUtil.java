package reveila.util.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Charles Lee
 *
 * This utility class provides convenience methods for file system operations.
 */
public final class FileUtil {

	/**
	 * Private constructor to prevent instantiation of this utility class.
	 */
	private FileUtil() {}

	/**
	 * Deletes a file or directory. If the file is a directory, it will be deleted recursively.
	 *
	 * @param file the file or directory to delete.
	 * @param throwException if true, throws a {@link FileDeleteException} if any file could not be deleted.
	 * @throws FileDeleteException if deletion fails and throwException is true.
	 */
	public static void delete(File file, boolean throwException) throws FileDeleteException {
		if (file == null || !file.exists()) {
			return;
		}

		List<File> failedFiles = new LinkedList<>();
		deleteRecursively(file, failedFiles);

		if (throwException && !failedFiles.isEmpty()) {
			throw new FileDeleteException("Failed to delete one or more files.", failedFiles.toArray(new File[0]));
		}
	}

	private static void deleteRecursively(File file, List<File> failedFiles) {
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) {
					deleteRecursively(child, failedFiles);
				}
			}
		}

		if (!file.delete()) {
			failedFiles.add(file);
		}
	}

	/**
	 * Lists all files in a given directory that end with a specific extension.
	 *
	 * @param dirPath the path to the directory.
	 * @param extension the file extension to filter by (without the dot).
	 * @return an array of {@link File} objects, or an empty array if the directory does not exist or is not a directory.
	 */
	public static File[] listFilesWithExtension(String dirPath, String extension) {
		File dir = new File(dirPath);
		if (!dir.isDirectory()) {
			return new File[0];
		}
		// Ensure the extension starts with a dot for the filter.
		final String dotExtension = extension.startsWith(".") ? extension : "." + extension;
		return dir.listFiles((d, name) -> name.toLowerCase().endsWith(dotExtension.toLowerCase()));
	}

	/**
	 * Copies a source file to a target file or directory.
	 *
	 * @param source the source file.
	 * @param target the target file or directory.
	 * @param overwrite if true, existing target file will be overwritten.
	 * @return the number of bytes copied.
	 * @throws IOException if the copy operation fails.
	 */
	public static long copyFile(File source, File target, boolean overwrite) throws IOException {
		if (source == null) {
			throw new IOException("Source file specified is null");
		}
		if (!source.exists()) {
			throw new IOException("Source file specified could not be found: " + source.getAbsolutePath());
		}
		if (!source.isFile()) {
			throw new IOException("Source file specified does not denote a normal file: " + source.getAbsolutePath());
		}
		if (target == null) {
			throw new IOException("Destination file specified is null");
		}

		Path sourcePath = source.toPath();
		Path targetPath = target.toPath();

		// Determine the final destination path. If target is a directory, resolve a file with the same name inside it.
		Path finalTargetPath = Files.isDirectory(targetPath) ? targetPath.resolve(source.getName()) : targetPath;

		// Ensure parent directories of the final target file exist.
		Path parentDir = finalTargetPath.getParent();
		if (parentDir != null && !Files.exists(parentDir)) {
			Files.createDirectories(parentDir);
		}

		if (overwrite) {
			Files.copy(sourcePath, finalTargetPath, StandardCopyOption.REPLACE_EXISTING);
		} else {
			// Throws FileAlreadyExistsException (which is a subclass of IOException) if the file exists.
			Files.copy(sourcePath, finalTargetPath);
		}
		return Files.size(finalTargetPath);
	}

	/**
	 * Copies a source directory and its contents to a target directory.
	 *
	 * @param source the source directory.
	 * @param target the target directory.
	 * @param overwrite if true, existing files will be overwritten.
	 * @return the total bytes copied.
	 * @throws IOException if the operation fails.
	 */
	public static long copyDir(File source, File target, boolean overwrite) throws IOException {
		if (source == null) {
			throw new IOException("Source directory specified is null");
		}
		if (!source.exists()) {
			throw new IOException("Source directory does not exist: " + source.getAbsolutePath());
		}
		if (!source.isDirectory()) {
			throw new IOException("Source is not a directory: " + source.getAbsolutePath());
		}
		if (target == null) {
			throw new IOException("Target directory specified is null");
		}
		if (target.exists() && !target.isDirectory()) {
			throw new IOException("Target exists and is not a directory: " + target.getAbsolutePath());
		}

		// Create the destination directory if it doesn't exist
		if (!target.exists()) {
			if (!target.mkdirs()) {
				throw new IOException("Could not create target directory: " + target.getAbsolutePath());
			}
		}

		File[] srcFiles = source.listFiles();
		if (srcFiles == null) {
			throw new IOException("Could not list files in source directory: " + source.getAbsolutePath());
		}

		long bytes = 0;
		for (File srcFile : srcFiles) {
			File destFile = new File(target, srcFile.getName());
			if (srcFile.isDirectory()) {
				bytes += copyDir(srcFile, destFile, overwrite);
			} else {
				bytes += copyFile(srcFile, destFile, overwrite);
			}
		}
		return bytes;
	}

	/**
	 * Gets the name of a file from a full path, without the extension.
	 * @param filename the full path or name of the file.
	 * @return the file name without its extension.
	 */
	public static String getFilenameWithoutExtension(String filename) {
		if (filename == null || filename.isEmpty()) {
			return filename;
		}
		// Use File to handle both full paths and simple filenames correctly.
		String name = new File(filename).getName();
		int dotIndex = name.lastIndexOf('.');
		// If there is no dot, or if the dot is the first character (e.g., ".bashrc"),
		// return the full name.
		return (dotIndex <= 0) ? name : name.substring(0, dotIndex);
	}
}
