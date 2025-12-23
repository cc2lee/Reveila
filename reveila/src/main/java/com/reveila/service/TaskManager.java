package com.reveila.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EventObject;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.reveila.system.AbstractService;
import com.reveila.system.Constants;
import com.reveila.system.JsonConfiguration;
import com.reveila.system.MetaObject;
import com.reveila.system.PlatformAdapter;
import com.reveila.system.Proxy;

public class TaskManager extends AbstractService implements Runnable {

    private long interval = 60000; // 1 minute
    private long initialDelay = 60000; // Default to 1 minute
    
    public TaskManager() {
        super();
    }

    @Override
    public void onEvent(EventObject evtObj) throws Exception {}

    @Override
    public void start() throws Exception {
        PlatformAdapter platformAdapter = this.systemContext.getPlatformAdapter();
        Logger logger = this.systemContext.getLogger(this);
        platformAdapter.runTask(this, this.initialDelay, this.interval, this);
        logger.info("Task Manager started with interval: " + this.interval + " ms, with initial delay: " + this.initialDelay + " ms.");
    }

    private String[] getTaskConfFiles() throws IOException {
        PlatformAdapter platformAdapter = this.systemContext.getPlatformAdapter();
        return platformAdapter.listTaskConfigs();
    }

    @Override
    public void run() { // A PlatformAdapter will call this method periodically based on the configured interval
        Logger logger = this.systemContext.getLogger(this);
        PlatformAdapter platformAdapter = this.systemContext.getPlatformAdapter();
        String[] paths;
        try {
            paths = getTaskConfFiles();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to list task configuration files.", e);
            return;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error occurred while listing task configuration files.", e);
            return;
        }

        if (paths == null || paths.length == 0) {
			logger.info("No tasks found to run.");
            return;
		}

		for (String path : paths) {
			if (path.toLowerCase(Locale.ROOT).endsWith(".json")) { // only process JSON files
                JsonConfiguration confGroup = null;
				try (InputStream is = platformAdapter.getInputStream(PlatformAdapter.TASK_STORAGE, path)) { // path is relative to task storage
					logger.info("Loading tasks from: " + path);
					confGroup = new JsonConfiguration(is, logger);
					List<MetaObject> taskList = confGroup.read();
					for (MetaObject task : taskList) {
						if (task.isStartOnLoad()) { // The task is enabled. Run it if it's due.
                            JobSchedule schedule = parseSchedule(task, logger);
                            
                            if (schedule == null) {
                                logger.warning("Invalid schedule for task: " + task.getName() + ". Skipping execution.");
                                continue;
                            }

                            if (!schedule.isDue()) {
                                continue; // Job is not due to run
                            }

                            logger.info("Starting task: " + task.getName());
							
							Proxy proxy = new Proxy(task);
                            try {
                                proxy.setSystemContext(this.systemContext);
							    proxy.start();
                                proxy.invoke("run", null);

                                if (schedule.lastRunMapObject != null) { // Update last run time in the task configuration
                                    String timestamp = DateTimeFormatter.ofPattern(Constants.JOB_DATE_FORMAT)
                                            .withZone(ZoneId.systemDefault())
                                            .format(Instant.now());
                                    schedule.lastRunMapObject.put("value", timestamp);
                                }
                            } catch (Exception e) {
                                logger.log(Level.SEVERE, "Task '" + task.getName() + "' failed during execution.", e);
                            } finally {
                                try {
                                    proxy.stop();
                                } catch (Exception e) {
                                    logger.log(Level.WARNING, "Failed to stop task '" + task.getName() + "'.", e);
                                }
                            }
						}
					} // for each task
                    
				} catch (Exception e) {
                    // try-with-resources handles closing the stream
                    logger.log(Level.SEVERE, "Failed to load task(s) from " + path + ".", e);
				} finally {
                    if (confGroup != null) {
                        try (OutputStream os = platformAdapter.getOutputStream(PlatformAdapter.TASK_STORAGE, path)) {
                            confGroup.writeToStream(os);
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "Failed to save task run states to " + path + ".", e);
                        }
                    }
                }
			}
		}
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }

    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    private JobSchedule parseSchedule(MetaObject metaObject, Logger logger) {
        List<Map<String, Object>> args = metaObject.getArguments();
        if (args == null) {
            return null;
        }

        long lastRun = -1L;
        long delay = -1L;
        Map<String, Object> lastRunMapObject = null;

        for (Map<String, Object> arg : args) {
            String argName = (String) arg.get("name");
            if (Constants.JOB_ARG_LASTRUN.equalsIgnoreCase(argName)) {
                lastRunMapObject = arg;
                String dateStr = (String) arg.get("value");
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.JOB_DATE_FORMAT).withZone(ZoneId.systemDefault());
                    lastRun = Instant.from(formatter.parse(dateStr)).toEpochMilli();
                } catch (Exception e) {
                    logger.warning("Could not parse Last-Run date for job '" + metaObject.getName() + "': " + dateStr);
                    return null; // Invalid schedule
                }
            } else if (Constants.JOB_ARG_DELAY.equalsIgnoreCase(argName)) {
                Object delayValue = arg.get("value");
                if (delayValue instanceof Number) {
                    delay = ((Number) delayValue).longValue();
                } else {
                    logger.warning("Invalid delay value for job '" + metaObject.getName() + "': " + delayValue);
                    return null; // Invalid schedule
                }
            }
        }
        return new JobSchedule(lastRun, delay, lastRunMapObject);
    }

    private static class JobSchedule {
        final long lastRun;
        final long delay;
        final Map<String, Object> lastRunMapObject;

        JobSchedule(long lastRun, long delay, Map<String, Object> lastRunMapObject) {
            this.lastRun = lastRun;
            this.delay = delay;
            this.lastRunMapObject = lastRunMapObject;
        }

        boolean isDue() {
            return lastRun != -1 && delay != -1 && (System.currentTimeMillis() - lastRun) >= delay;
        }
    }
}
