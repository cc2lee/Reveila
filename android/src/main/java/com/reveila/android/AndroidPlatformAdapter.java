package com.reveila.android;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.reveila.data.Entity;
import com.reveila.data.Repository;
import com.reveila.system.BasePlatformAdapter;
import com.reveila.system.Constants;
import com.reveila.android.db.ReveilaDatabase;
import com.reveila.android.data.RoomRepository;

/**
 * An implementation of {@link PlatformAdapter} for the Android environment.
 */
public class AndroidPlatformAdapter extends BasePlatformAdapter {

    private final Context context;
    private AndroidCryptographer cryptographer;

    public AndroidPlatformAdapter(Context context) throws Exception {
        this(context, new Properties());
    }

    public AndroidPlatformAdapter(Context context, Properties overwrites) throws Exception {
        super(prepareProperties(context, overwrites));
        this.context = context;
        // Re-configure logging to include logcat
        configureAndroidLogging();
    }

    public Context getAndroidContext() {
        return this.context;
    }

    private static Properties prepareProperties(Context context, Properties overwrites) {
        Properties props = new Properties();
        String defaultHome = new File(context.getFilesDir(), "reveila/system").getAbsolutePath();
        props.setProperty(Constants.SYSTEM_HOME, defaultHome);
        if (overwrites != null) {
            props.putAll(overwrites);
        }
        return props;
    }

    @Override
    public String getPlatformDescription() {
        return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }



    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public synchronized Repository<Entity, Map<String, Map<String, Object>>> getRepository(String entityType) {
        Repository<Entity, Map<String, Map<String, Object>>> existingRepo = super.getRepository(entityType);
        if (existingRepo != null) {
            return existingRepo;
        }

        ReveilaDatabase db = ReveilaDatabase.Companion.getDatabase(context);
        Repository repo = new RoomRepository(entityType, db.genericDao());

        registerRepository(entityType, repo);
        return repo;
    }

    @Override
    public com.reveila.crypto.Cryptographer getCryptographer() {
        if (this.cryptographer == null) {
            try {
                this.cryptographer = new AndroidCryptographer();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to initialize AndroidCryptographer", e);
            }
        }
        return this.cryptographer;
    }

    private void configureAndroidLogging() {
        Logger rootLogger = Logger.getLogger("");
        Handler logcatHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record == null) return;
                String tag = record.getLoggerName() != null && !record.getLoggerName().isEmpty() ? record.getLoggerName() : "reveila-android";
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
        rootLogger.addHandler(logcatHandler);
    }
}
