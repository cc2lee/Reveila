package reveila.service;

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

import reveila.platform.PlatformAdapter;
import reveila.system.AbstractService;
import reveila.system.Constants;
import reveila.system.JsonConfiguration;
import reveila.system.MetaObject;
import reveila.system.Proxy;

public class TaskManager extends AbstractService implements Runnable {

    private long interval = 60000; // 1 minute
    private long initialDelay = 60000; // Default to 1 minute
    
    public TaskManager() {
        super();
    }

    @Override
    public void onEvent(EventObject evtObj) throws Exception {
        // Not used in this implementation because this class is also the task runner.
        // The run() method is called by the PlatformAdapter's scheduling mechanism.
    }

    @Override
    public void start() throws Exception {
        PlatformAdapter platformAdapter = this.systemContext.getPlatformAdapter();
        Logger logger = this.systemContext.getLogger(this);
        if (platformAdapter == null) {
            throw new IllegalStateException("PlatformAdapter is not set in SystemContext.");
        }
        if (logger == null) {
            throw new IllegalStateException("Logger is not found for " + this.getClass().getName() + ".");
        }
        platformAdapter.runTask(this, this.initialDelay, this.interval, this);
        logger.info("Task Manager started with interval: " + this.interval + " ms and initial delay: " + this.initialDelay + " ms.");
    }

    private String[] getTaskConfFiles() throws IOException {
        PlatformAdapter platformAdapter = this.systemContext.getPlatformAdapter();
        return platformAdapter.listTaskConfigs();
    }

    @Override
    public void run() {
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
			if (path.toLowerCase(Locale.ROOT).endsWith(".json")) {
                JsonConfiguration confGroup = null;
				try (InputStream is = platformAdapter.getInputStream(PlatformAdapter.TASK_STORAGE, path)) {
					logger.info("Loading tasks from: " + path);
					confGroup = new JsonConfiguration(is, logger);
					List<MetaObject> taskList = confGroup.read();
					for (MetaObject task : taskList) {
						if (task.isStartOnLoad()) { // enabled
                            JobSchedule schedule = parseSchedule(task, logger);
                            if (schedule == null || !schedule.isDue()) {
                                continue; // Job is not due to run
                            }

                            // Update the last run time immediately before running the job.
                            // This prevents the same job from being queued multiple times.
                            if (schedule.lastRunMapObject != null) {
                                String formatted = DateTimeFormatter.ofPattern(Constants.C_JOB_DATE_FORMAT)
                                    .withZone(ZoneId.systemDefault())
                                    .format(Instant.now());
                                schedule.lastRunMapObject.put("value", formatted);
                            }

                            logger.info("Starting task: " + task.getName());
							
							Proxy proxy = new Proxy(task);
                            try {
                                proxy.setSystemContext(this.systemContext);
							    proxy.start();
                                proxy.invoke("run", null);
                            } catch (Exception e) {
                                logger.log(Level.SEVERE, "Task '" + task.getName() + "' failed during execution.", e);
                            } finally {
                                try {
                                    proxy.stop();
                                } catch (Exception e) {
                                    // ignore,
                                    // if successful, we don't care about stop errors
                                    // if failed, it will be logged above
                                }
                            }
						}
					} // for each task
                    
				} catch (Exception e) {
                    // try-with-resources handles closing the stream
                    logger.log(Level.SEVERE, "Failed to load tasks from " + path + ".", e);
				} finally {
                    if (confGroup != null) {
                        try (OutputStream os = platformAdapter.getOutputStream(PlatformAdapter.TASK_STORAGE, path)) {
                            confGroup.writeToStream(os);
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "Failed to save tasks to " + path + ".", e);
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
            if (Constants.C_JOB_ARG_LASTRUN.equalsIgnoreCase(argName)) {
                lastRunMapObject = arg;
                String dateStr = (String) arg.get("value");
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.C_JOB_DATE_FORMAT).withZone(ZoneId.systemDefault());
                    lastRun = Instant.from(formatter.parse(dateStr)).toEpochMilli();
                } catch (Exception e) {
                    logger.warning("Could not parse LastRun date for job '" + metaObject.getName() + "': " + dateStr);
                    return null; // Invalid schedule
                }
            } else if (Constants.C_JOB_ARG_DELAY.equalsIgnoreCase(argName)) {
                Object delayValue = arg.get("value");
                if (delayValue instanceof Number) {
                    delay = ((Number) delayValue).longValue();
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
