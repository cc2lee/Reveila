package reveila.service;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import reveila.system.AbstractService;
import reveila.system.Constants;
import reveila.system.JsonConfiguration;
import reveila.system.MetaObject;
import reveila.util.io.FileUtil;
import reveila.util.task.Job;
import reveila.util.task.JobEvent;
import reveila.util.task.JobEventType;
import reveila.util.task.JobException;
import reveila.util.task.JobStatus;

public class TaskManager extends AbstractService implements Runnable {

    private String jobConfigDir;
    private Thread workerThread;
    private volatile boolean stop = false;
    private long interval = 60000; // 1 minute
    private long initialDelay = 0; // Default to no delay
    private int jobThreadPoolSize = 5; // Default to 5 concurrent jobs
    private ExecutorService jobExecutor;

    public TaskManager() {
        super();
    }

    @Override
    public void start() throws Exception {
        // Resolve job directory once at the start.
        if (jobConfigDir == null || jobConfigDir.isBlank()) {
            String homeDir = systemContext.getProperties().getProperty(Constants.S_SYSTEM_HOME);
                jobConfigDir = homeDir + File.separator + Constants.C_CONFIGS_DIR_NAME 
                    + File.separator + Constants.C_JOB_DIR_NAME;
        }

        // Use a ThreadFactory to name worker threads for easier debugging.
        final ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "TaskManager-Job-" + count.incrementAndGet());
            }
        };
        this.jobExecutor = Executors.newFixedThreadPool(jobThreadPoolSize, threadFactory);
        this.workerThread = new Thread(this, "TaskManager-Scheduler");
        this.workerThread.start();
    }

    @Override
    public void run() {
        Logger logger = systemContext.getLogger(TaskManager.this);
        logger.info("Task Manager thread started. Monitoring task configuration directory: " + jobConfigDir);

        // Add an initial delay if configured, to allow the main application to fully initialize.
        if (initialDelay > 0) {
            try {
                logger.info("Task Manager waiting for initial delay: " + initialDelay + "ms");
                Thread.sleep(initialDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Preserve the interrupted status
                logger.warning("Task Manager initial delay interrupted, stopping.");
                return; // Exit if interrupted during initial delay
            }
        }

        while (!stop) {
            try {
                checkAndRunJobs(logger);
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                // Interrupted, likely by stop(). The loop condition will handle termination.
                Thread.currentThread().interrupt(); // Preserve the interrupted status
            } catch (Exception e) {
                // Catch any other unexpected errors to prevent the manager from dying.
                logger.log(Level.SEVERE, "Unexpected error in TaskManager main loop", e);
            }
        }
        logger.info("Task Manager thread stopped.");
    }

    private void checkAndRunJobs(Logger logger) {
        File[] files = FileUtil.listFilesWithExtension(jobConfigDir, "json");
        if (files == null) {
            logger.warning("Failed to access or list files in task definitions directory: " + jobConfigDir);
            return;
        }

        for (File f : files) {
            try {
                JsonConfiguration group = new JsonConfiguration(f.getAbsolutePath(), logger);
                List<MetaObject> list = group.read();

                if (list != null) {
                    for (MetaObject item : list) {
                        processJob(item, logger);
                    }
                }
                group.writeToFile();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to process job configuration file: " + f.getAbsolutePath(), e);
            }
        }
    }

    private void processJob(MetaObject jobDefinition, Logger logger) {
        if (!jobDefinition.isStartOnLoad()) {
            logger.info("Job '" + jobDefinition.getName() + "' is not set to start on load. Skipping.");
            return;
        }
        JobSchedule schedule = parseSchedule(jobDefinition, logger);
        if (schedule == null || !schedule.isDue()) {
            return; // Job is not due to run
        }

        // Update the last run time immediately before submitting the job.
        // This prevents the same job from being queued multiple times.
        if (schedule.lastRunMapObject != null) {
            String formatted = DateTimeFormatter.ofPattern(Constants.C_JOB_DATE_FORMAT)
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
            schedule.lastRunMapObject.put("value", formatted);
        }

        // Submit the job to run on the thread pool.
        jobExecutor.submit(() -> {
            Job job = null;
            try {
                Object object = jobDefinition.newObject(logger);
                if (!(object instanceof Job)) {
                    logger.warning("Component '" + jobDefinition.getName() + "' is not an instance of a Job. Skipping execution.");
                    return;
                }
                job = (Job) object;
                job.setSystemContext(this.systemContext);

                fireJobEvent(new JobEvent(job, JobEventType.JOB_STARTED, System.currentTimeMillis()));
                logger.info("Executing job: " + jobDefinition.getName());
                job.run();
                fireJobEvent(new JobEvent(job, JobEventType.JOB_FINISHED, System.currentTimeMillis()));
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to instantiate or run Job object: " + jobDefinition.getName(), e);
                if (job != null) {
                    job.setStatus(JobStatus.FAILED);
                    job.setException(new JobException("Job execution failed unexpectedly.", e));
                    fireJobEvent(new JobEvent(job, JobEventType.JOB_FAILED, System.currentTimeMillis()));
                }
            }
        });
    }

    @Override
    public void stop() {
        stop = true;
        // Gracefully shut down the job executor pool.
        if (this.jobExecutor != null) {
            this.jobExecutor.shutdown();
            try {
                if (!this.jobExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    this.jobExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                this.jobExecutor.shutdownNow();
            }
        }
        if (this.workerThread != null) {
            // Interrupt the thread to wake it from sleep() and allow it to terminate gracefully.
            this.workerThread.interrupt();
        }
    }

    public String getJobConfigDir() {
        return jobConfigDir;
    }

    public void setJobConfigDir(String jobConfigurationDirectory) {
        this.jobConfigDir = jobConfigurationDirectory;
    }

    private void fireJobEvent(EventObject evt) {
        systemContext.getEventManager().dispatchEvent(evt);
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }

    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    public void setJobThreadPoolSize(int jobThreadPoolSize) {
        this.jobThreadPoolSize = jobThreadPoolSize;
    }

    private JobSchedule parseSchedule(MetaObject jobDefinition, Logger logger) {
        List<Map<String, Object>> args = jobDefinition.getArguments();
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
                    logger.warning("Could not parse LastRun date for job '" + jobDefinition.getName() + "': " + dateStr);
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
