package reveila.service.task;

import java.io.File;
import java.util.logging.Level;

import reveila.util.FileUtil;

public class DeleteFiles extends AbstractTask {

	public DeleteFiles() {
		// The base Job class now has a no-arg constructor.
	}

	private String[] dirs;
	private String[] excludeFilters;
	
	@Override
	public void run() {
		if (dirs == null || dirs.length == 0) {
			systemContext.getLogger(this).info("No directories configured to clean, skipping job.");
			return;
		}
		
		for (String dir : dirs) {
			try {
				systemContext.getLogger(this).info("Cleaning directory: " + dir);
				File[] files = new File(dir).listFiles();
				if (files != null) {
					for (File file : files) {
						if (shouldExclude(file)) {
							systemContext.getLogger(this).fine("Skipping excluded file: " + file.getAbsolutePath());
							continue;
						}
						FileUtil.delete(file, true);
					}
				}
			} catch (Exception e) {
				systemContext.getLogger(this).log(Level.SEVERE, "Failed to clean directory: " + dir, e);
				e.printStackTrace();
			}
		}
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
