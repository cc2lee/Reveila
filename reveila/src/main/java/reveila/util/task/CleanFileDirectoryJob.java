package reveila.util.task;

import java.io.File;
import java.util.logging.Level;

import reveila.util.io.FileUtil;

public class CleanFileDirectoryJob extends Job {

	public CleanFileDirectoryJob() {
		// The base Job class now has a no-arg constructor.
	}

	private String[] dirs;
	private String[] excludeFilters;
	
	@Override
	public void run() {
		setStatus(JobStatus.IN_PROGRESS);
		boolean errorsOccurred = false;

		if (dirs == null || dirs.length == 0) {
			logger.info("No directories configured to clean, skipping job.");
			setStatus(JobStatus.SUCCESSFUL);
			setPercentageCompleted(100.0);
			return;
		}
		
		for (String dir : dirs) {
			try {
				logger.info("Cleaning directory: " + dir);
				File[] files = new File(dir).listFiles();
				if (files != null) {
					for (File file : files) {
						if (shouldExclude(file)) {
							logger.fine("Skipping excluded file: " + file.getAbsolutePath());
							continue;
						}
						FileUtil.delete(file, true);
					}
				}
			} catch (Exception e) {
				// Log the full exception for better diagnostics, without swallowing the exception type.
				logger.log(Level.SEVERE, "Failed to clean directory: " + dir, e);
				errorsOccurred = true;
			}
		}
		
		if (errorsOccurred) {
			setStatus(JobStatus.COMPLETED_WITH_ERRORS);
			setMessage("Completed with one or more errors. Check logs for details.");
		} else {
			setStatus(JobStatus.SUCCESSFUL);
			setMessage("All configured directories cleaned successfully.");
		}
		setPercentageCompleted(100.0);
	}

	public void setDirs(String[] filePaths) {
		this.dirs = filePaths;
	}

	public void setExcludeFilters(String[] filters) {
		this.excludeFilters = filters;
	}

	private boolean shouldExclude(File file) {
		if (excludeFilters == null || excludeFilters.length == 0) {
			return false;
		}
		String fileName = file.getName().toLowerCase();
		for (String filter : excludeFilters) {
			// Simple, case-insensitive suffix check.
			if (filter != null && !filter.isBlank() && fileName.endsWith(filter.toLowerCase())) {
				return true;
			}
		}
		return false;
	}
}
