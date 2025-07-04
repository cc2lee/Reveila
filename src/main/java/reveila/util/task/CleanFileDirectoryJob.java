package reveila.util.task;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

import reveila.util.io.FileDeleteException;
import reveila.util.io.FileUtil;

public class CleanFileDirectoryJob extends Job {

	public CleanFileDirectoryJob(String configFilePath) {
		super(configFilePath);
	}

	private String[] dirs;
	
	public void doTask() {
		if (dirs == null || dirs.length == 0) {
			return;
		}
		
		for (String dir : dirs) {
			try {
				File[] files = new File(dir).listFiles();
				for (File file : files) {
					FileUtil.delete(file, true);
				}
			} catch (FileDeleteException e) {
				StringWriter sw = new StringWriter();
    			e.printStackTrace(new PrintWriter(sw));
    			logger.severe(sw.toString());
			}
		}
		
	}

	public void setDirsToClean(String[] filePaths) {
		this.dirs = filePaths;
	}
}
