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
import com.reveila.data.JsonFileRepository;
import com.reveila.data.Page;
import com.reveila.data.Repository;
import com.reveila.system.BasePlatformAdapter;
import com.reveila.system.Constants;

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

        File dataDir = new File(getSystemHome().toFile(), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        JsonFileRepository<Map, String> jsonRepo = new JsonFileRepository<>(
            dataDir.getAbsolutePath(), 
            entityType, 
            Map.class, 
            String.class,
            this
        );
        
        Repository<Entity, Map<String, Map<String, Object>>> repo = new Repository<Entity, Map<String, Map<String, Object>>>() {
            @Override
            public Page<Entity> fetchPage(com.reveila.data.Filter filter, com.reveila.data.Sort sort, List<String> fetches, int page, int size, boolean includeCount) {
                Page<Map> mapPage = jsonRepo.fetchPage(filter, sort, fetches, page, size, includeCount);
                return mapPage.map(m -> mapToEntity(entityType, m));
            }

            @Override
            public Entity store(Entity entity) {
                Map<String, Object> attributes = new HashMap<>(entity.getAttributes());
                Map<String, Map<String, Object>> key = entity.getKey();
                if (key.containsKey("id")) {
                    attributes.put("id", key.get("id").get("value"));
                }
                
                Map savedMap = jsonRepo.store(attributes);
                return mapToEntity(entityType, savedMap);
            }

            @Override
            public Optional<Entity> fetchById(Map<String, Map<String, Object>> idMap) {
                String id = extractId(idMap);
                return jsonRepo.fetchById(id).map(m -> mapToEntity(entityType, m));
            }

            @Override
            public void disposeById(Map<String, Map<String, Object>> idMap) {
                jsonRepo.disposeById(extractId(idMap));
            }

            @Override
            public List<Entity> storeAll(java.util.Collection<Entity> entities) {
                List<Map> maps = new ArrayList<>();
                for (Entity e : entities) {
                    Map<String, Object> m = new HashMap<>(e.getAttributes());
                    if (e.getKey().containsKey("id")) {
                        m.put("id", e.getKey().get("id").get("value"));
                    }
                    maps.add(m);
                }
                return jsonRepo.storeAll(maps).stream().map(m -> mapToEntity(entityType, m)).collect(java.util.stream.Collectors.toList());
            }

            @Override
            public long count() {
                return jsonRepo.count();
            }

            @Override
            public boolean hasId(Map<String, Map<String, Object>> idMap) {
                return jsonRepo.hasId(extractId(idMap));
            }

            @Override
            public void commit() {
                jsonRepo.commit();
            }

            @Override
            public String getType() {
                return entityType;
            }

            @Override
            public List<Entity> fetchAll() {
                return jsonRepo.fetchAll().stream().map(m -> mapToEntity(entityType, m)).collect(java.util.stream.Collectors.toList());
            }

            private Entity mapToEntity(String type, Map m) {
                Map<String, Map<String, Object>> key = new HashMap<>();
                Map<String, Object> idPart = new HashMap<>();
                idPart.put("value", m.get("id"));
                key.put("id", idPart);
                return new Entity(type, key, m);
            }

            private String extractId(Map<String, Map<String, Object>> idMap) {
                if (idMap.containsKey("id")) {
                    return String.valueOf(idMap.get("id").get("value"));
                }
                return String.valueOf(idMap.values().iterator().next().get("value"));
            }
        };

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
