package com.reveila.android;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.*;

import com.reveila.system.Reveila;
import com.reveila.system.PlatformAdapter;
import com.reveila.system.Constants;
import com.reveila.event.EventConsumer;
import com.reveila.data.Entity;
import com.reveila.data.Repository;
import com.reveila.util.FileUtil;

/**
 * An implementation of {@link PlatformAdapter} for the Android environment.
 */
public class AndroidPlatformAdapter implements PlatformAdapter {

    private final Context context;
    private Properties properties;
    private Logger logger;
    private Reveila reveila;

    public AndroidPlatformAdapter(Context context) throws IOException {
        this.context = context;
        this.properties = new Properties();
        // Load default properties or from assets if needed
        this.logger = configureLogging(this.properties);
    }

    @Override
    public String getPlatformDescription() {
        return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }

    @Override
    public Properties getProperties() {
        return this.properties;
    }

    @Override
    public InputStream getFileInputStream(String relativePath) throws IOException {
        File file = new File(getSystemHome(context), relativePath);
        return new FileInputStream(file);
    }

    @Override
    public OutputStream getFileOutputStream(String relativePath, boolean append) throws IOException {
        File file = new File(getSystemHome(context), relativePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        return new FileOutputStream(file, append);
    }

    @Override
    public String[] getConfigFilePaths() throws IOException {
        File componentsDir = new File(getSystemHome(context), Constants.CONFIGS_DIR_NAME + File.separator + "components");
        if (!componentsDir.exists()) return new String[0];
        
        File[] files = componentsDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return new String[0];
        
        return java.util.Arrays.stream(files)
                .map(f -> Constants.CONFIGS_DIR_NAME + File.separator + "components" + File.separator + f.getName())
                .toArray(String[]::new);
    }

    @Override
    public Logger getLogger() {
        return this.logger;
    }

    @Override
    public void registerAutoCall(String componentName, String methodName, long delaySeconds, long intervalSeconds, EventConsumer eventConsumer) throws Exception {
        // TODO: Implement Android specific scheduling (WorkManager or AlarmManager)
    }

    @Override
    public void unregisterAutoCall(String componentName) {
        // TODO: Implement Android specific scheduling cancellation
    }

    @Override
    public void plug(Reveila reveila) {
        this.reveila = reveila;
    }

    @Override
    public void unplug() {
        this.reveila = null;
    }

    @Override
    public Repository<Entity, Map<String, Map<String, Object>>> getRepository(String entityType) {
        // TODO: Return an Android specific repository implementation
        return null;
    }

    public static String getSystemHome(Context context) {
        return new File(context.getFilesDir(), "reveila/system").getAbsolutePath();
    }

    private Logger configureLogging(Properties props) {
        Logger newLogger = Logger.getLogger("reveila-android");
        newLogger.setUseParentHandlers(false);
        
        Handler logcatHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record == null) return;
                String tag = record.getLoggerName();
                String msg = record.getMessage();
                int level = record.getLevel().intValue();

                if (level >= Level.SEVERE.intValue()) {
                    android.util.Log.e(tag, msg);
                } else if (level >= Level.WARNING.intValue()) {
                    android.util.Log.w(tag, msg);
                } else if (level >= Level.INFO.intValue()) {
                    android.util.Log.i(tag, msg);
                } else {
                    android.util.Log.d(tag, msg);
                }
            }
            @Override
            public void flush() {}
            @Override
            public void close() throws SecurityException {}
        };
        
        newLogger.addHandler(logcatHandler);
        return newLogger;
    }
}
