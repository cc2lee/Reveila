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
import com.reveila.data.GenericRepository;
import com.reveila.data.JsonFileRepository;
import com.reveila.data.EntityMapper;
import com.reveila.data.Entity;
import com.reveila.data.Repository;
import com.reveila.data.Page;
import com.reveila.system.Reveila;
import com.reveila.system.PlatformAdapter;
import com.reveila.system.Constants;
import com.reveila.event.EventConsumer;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * An implementation of {@link PlatformAdapter} for the Android environment.
 */
public class AndroidPlatformAdapter implements PlatformAdapter {

    private final Context context;
    private Properties properties;
    private Logger logger;
    private Reveila reveila;
    private final Map<String, Repository<Entity, Map<String, Map<String, Object>>>> repositories = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, java.util.concurrent.ScheduledFuture<?>> autoCalls = new HashMap<>();

    public AndroidPlatformAdapter(Context context) throws IOException {
        this(context, new Properties());
    }

    public AndroidPlatformAdapter(Context context, Properties initialProperties) throws IOException {
        this.context = context;
        this.properties = new Properties();
        if (initialProperties != null) {
            this.properties.putAll(initialProperties);
        }
        // Load default properties
        loadProperties();
        this.logger = configureLogging(this.properties);
    }

    private void loadProperties() {
        try (InputStream is = context.getAssets().open("reveila/system/configs/reveila.properties")) {
            properties.load(is);
        } catch (IOException e) {
            // Fallback or log error
            android.util.Log.w("AndroidPlatformAdapter", "Failed to load reveila.properties from assets", e);
        }
        
        // Ensure SYSTEM_HOME is set for Android, prioritizing existing value if set
        if (!properties.containsKey(Constants.SYSTEM_HOME)) {
            properties.setProperty(Constants.SYSTEM_HOME, getSystemHome(context));
        }
        
        // Use a default secret key if not provided (for development)
        if (!properties.containsKey(Constants.CRYPTOGRAPHER_SECRETKEY)) {
            properties.setProperty(Constants.CRYPTOGRAPHER_SECRETKEY, "ReveilaAndroidDefaultSecretKey123");
        }
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
        if (!file.exists()) {
            // Try to load from assets if not in filesystem
            return context.getAssets().open("reveila/" + relativePath);
        }
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
        
        List<String> paths = new ArrayList<>();
        
        // Add files from filesystem
        if (componentsDir.exists()) {
            File[] files = componentsDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    paths.add(Constants.CONFIGS_DIR_NAME + File.separator + "components" + File.separator + f.getName());
                }
            }
        }
        
        // If empty, try to list from assets (initial setup)
        if (paths.isEmpty()) {
            try {
                String[] assets = context.getAssets().list("reveila/system/configs/components");
                if (assets != null) {
                    for (String asset : assets) {
                        if (asset.endsWith(".json")) {
                            paths.add(Constants.CONFIGS_DIR_NAME + File.separator + "components" + File.separator + asset);
                        }
                    }
                }
            } catch (IOException e) {
                // Ignore
            }
        }
        
        return paths.toArray(new String[0]);
    }

    @Override
    public Logger getLogger() {
        return this.logger;
    }

    @Override
    public void registerAutoCall(String componentName, String methodName, long delaySeconds, long intervalSeconds, EventConsumer eventConsumer) throws Exception {
        unregisterAutoCall(componentName);
        
        java.util.concurrent.ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (reveila != null) {
                    reveila.invoke(componentName, methodName, new Object[0]);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in auto-call for component: " + componentName, e);
            }
        }, delaySeconds, intervalSeconds, TimeUnit.SECONDS);
        
        autoCalls.put(componentName, future);
    }

    @Override
    public void unregisterAutoCall(String componentName) {
        java.util.concurrent.ScheduledFuture<?> future = autoCalls.remove(componentName);
        if (future != null) {
            future.cancel(true);
        }
    }

    @Override
    public void plug(Reveila reveila) {
        this.reveila = reveila;
    }

    @Override
    public void unplug() {
        this.reveila = null;
        scheduler.shutdownNow();
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public synchronized Repository<Entity, Map<String, Map<String, Object>>> getRepository(String entityType) {
        if (repositories.containsKey(entityType)) {
            return repositories.get(entityType);
        }

        File dataDir = new File(getSystemHome(context), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        // For Android, we use JsonFileRepository by default for all entities.
        // We wrap it in a GenericRepository to match the Port's expected return type.
        // Since we are working with generic Entity DTOs, we use Entity as the type.
        
        // Note: In a more complex scenario, we would use specific POJOs.
        // For the "Property Bag" pattern, we can use a Map-based repository or similar.
        // Here we use Entity.class directly if the JsonFileRepository can handle it.
        
        JsonFileRepository<Map, String> jsonRepo = new JsonFileRepository<>(
            Paths.get(dataDir.getAbsolutePath()), 
            entityType, 
            Map.class, 
            String.class
        );
        
        // We need a way to map between Map and Entity
        // Since Entity is just a wrapper around a Map, we can provide a custom GenericRepository or similar.
        // For simplicity in this port, let's assume we want to store things as Maps in the JSON file.
        
        Repository<Entity, Map<String, Map<String, Object>>> repo = new Repository<Entity, Map<String, Map<String, Object>>>() {
            @Override
            public Page<Entity> fetchPage(com.reveila.data.Filter filter, com.reveila.data.Sort sort, List<String> fetches, int page, int size, boolean includeCount) {
                Page<Map> mapPage = jsonRepo.fetchPage(filter, sort, fetches, page, size, includeCount);
                return mapPage.map(m -> mapToEntity(entityType, m));
            }

            @Override
            public Entity store(Entity entity) {
                Map<String, Object> attributes = new HashMap<>(entity.getAttributes());
                // Ensure ID is present in attributes for JsonFileRepository's getId fallback
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

        repositories.put(entityType, repo);
        return repo;
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
