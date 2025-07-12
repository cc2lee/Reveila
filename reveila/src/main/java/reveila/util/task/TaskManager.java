package reveila.util.task;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import reveila.error.ConfigurationException;
import reveila.system.JsonConfiguration;
import reveila.system.MetaObject;
import reveila.system.Service;
import reveila.util.TimeFormat;
import reveila.util.io.FileUtil;

public class TaskManager extends Service {

    private String jobConfigurationDirectory;
    private ThreadGroup threadGroup = new ThreadGroup("Task execution thread group");
    private boolean stop = false;
    private long interval = 60000; // 1 minute

    public TaskManager(MetaObject objectDescriptor) throws Exception {
        super(objectDescriptor);
    }

    @Override
    public void start() throws Exception {
        Thread t = new Thread(threadGroup, 
            new Runnable() {
                public void run() { // the root run
                    Logger logger = systemContext.getLogger(TaskManager.this);

                    long start = System.currentTimeMillis();
                    long elapsed = 0L;

                    while (!stop && (elapsed = System.currentTimeMillis() - start) < interval) {
                        synchronized (this) {
                            try {
                                Thread.sleep(interval - elapsed);
                                break;
                            } catch (InterruptedException e) {
                                continue;
                            }
                        }
                    }
                    
                    logger.info("Task Manager started at " + TimeFormat.getInstance().format(start));
                    
                    File[] files = FileUtil.listFilesWithExtension(jobConfigurationDirectory, "json");
                    if (files == null) {
                        throw new RuntimeException("Failed to access directory: " + jobConfigurationDirectory);
                    } else if (files.length == 0) {
                        return; // no jobs
                    }

                    for (File f : files) {
                        JsonConfiguration group = null;
                        try {
                            group = new JsonConfiguration(f.getAbsolutePath());
                        } catch (ConfigurationException e) {
                            logger.severe("Failed to read task definitions: " + e.getMessage());
                        }

                        if (group == null) {
                            continue;
                        }
                        
                        List<MetaObject> list = null;
                        try {
                            list = group.read();
                        } catch (Exception e) {
                            logger.severe("Failed to read job definitions: " + e.getMessage());
                        }

                        if (list != null) {
                            for (MetaObject item : list) {
                                List<Map<String,Object>> args = item.getArguments();
                                if (args == null) continue;
                                long lastRun = -1L;
                                long delay = -1L;
                                Map<String,Object> lastRunMapObject = null;
                                for (Map<String,Object> arg : args) {
                                    if ("LastRun".equalsIgnoreCase((String)arg.get("name"))) {
                                        lastRunMapObject = arg;
                                        String dateStr = (String)arg.get("value");
                                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
                                        lastRun = Instant.from(formatter.parse(dateStr)).toEpochMilli();
                                        // System.out.println(lastRun);
                                    } else if ("Delay".equalsIgnoreCase((String)arg.get("name"))) {
                                        delay = (Long)arg.get("value");
                                    }
                                }
                                
                                if ((System.currentTimeMillis() - lastRun) < delay) {
                                    continue;
                                }

                                Object object = null;
                                try {
                                    object = item.newObject();
                                } catch (Exception e) {
                                    logger.severe("Failed to instanciate Job object: " + e.getMessage());
                                }
                                
                                if (object != null && object instanceof Job) {
                                    Job job = (Job) object;
                                    job.run();
                                    if (lastRunMapObject != null) {
                                        long millis = System.currentTimeMillis();
                                        String formatted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                            .withZone(ZoneId.systemDefault())
                                            .format(Instant.ofEpochMilli(millis));
                                        // System.out.println(formatted); // e.g., 2025-07-02 15:30:45
                                        lastRunMapObject.put("value", formatted);
                                    }
                                }
                            }
                        }

                        try {
                            group.writeToFile();
                        } catch (Exception e) {
                            logger.severe("Failed to write job execution data to file: " + f.getAbsolutePath() + ", " + e.getMessage());
                        }
                        
                    } // end for each job config file
                } // end of run
            }); // end of runnable
        t.start();
    }

    @Override
    public void stop() throws Exception {
        stop = true;
    }

    @Override
    public void kill() {
        threadGroup.notifyAll();
    }

    public String getJobConfigurationDirectory() {
        return jobConfigurationDirectory;
    }

    public void setJobConfigurationDirectory(String jobConfigurationDirectory) {
        this.jobConfigurationDirectory = jobConfigurationDirectory;
    }

    protected void fireJobEvent(EventObject evt) {
        systemContext.getEventManager().dispatchEvent(evt);
    }

}
