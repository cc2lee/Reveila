package com.reveila.system;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service to scan and register plugins dynamically.
 * Implements ADR 0010: Scaling Plugins via Autonomous Discovery.
 */
public class PluginScannerService extends AbstractService {

    private String pluginDir;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String MANIFEST_FILE = "plugin-manifest.json";

    @Override
    protected void onStart() throws Exception {
        this.pluginDir = systemContext.getProperties().getProperty("plugin.directory", "system-home/standard/plugins");
        logger.info("PluginScannerService started. Scanning directory: " + pluginDir);
        scanAndRegister();
    }

    @Override
    protected void onStop() throws Exception {
        logger.info("PluginScannerService stopped.");
    }

    public void scanAndRegister() {
        File folder = new File(pluginDir);
        if (!folder.exists() || !folder.isDirectory()) {
            logger.warning("Plugin directory does not exist: " + pluginDir);
            return;
        }

        File[] plugins = folder.listFiles((dir, name) -> name.endsWith(".jar") || new File(dir, name).isDirectory());

        if (plugins != null) {
            for (File plugin : plugins) {
                try {
                    MetaObject mObj = loadManifest(plugin);
                    if (isValid(mObj)) {
                        // Register into SystemContext for proxy creation
                        Proxy proxy = new Proxy(mObj);
                        proxy.setName(mObj.getName());
                        systemContext.add(proxy);
                        logger.info("Discovered and Registered Plugin: " + mObj.getName() + " v" + mObj.getVersion());
                        
                        // Start the plugin (self-contained, order doesn't matter)
                        if (mObj.isAutoStart()) {
                            try {
                                proxy.start();
                                logger.info("✅ Started Plugin: " + mObj.getName());
                            } catch (Exception startEx) {
                                logger.severe("❌ Failed to start Plugin: " + mObj.getName() + ". Error: " + startEx.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.severe("Failed to load plugin manifest from: " + plugin.getName() + ". Error: " + e.getMessage());
                }
            }
        }
    }

    private MetaObject loadManifest(File plugin) throws Exception {
        if (plugin.isDirectory()) {
            File manifestFile = new File(plugin, MANIFEST_FILE);
            if (!manifestFile.exists()) throw new Exception("Manifest missing in directory");
            try (InputStream is = Files.newInputStream(manifestFile.toPath())) {
                java.util.Map<String, Object> map = mapper.readValue(is, java.util.Map.class);
                if (map.containsKey("plugin")) {
                    java.util.Map<String, Object> pMap = (java.util.Map<String, Object>) map.get("plugin");
                    pMap.put("componentType", "plugin");
                    return new MetaObject(pMap);
                }
                map.put("componentType", "plugin");
                return new MetaObject(map);
            }
        } else {
            // It's a JAR
            try (ZipFile zip = new ZipFile(plugin)) {
                ZipEntry entry = zip.getEntry(MANIFEST_FILE);
                if (entry == null) throw new Exception("Manifest missing in JAR");
                try (InputStream is = zip.getInputStream(entry)) {
                    java.util.Map<String, Object> map = mapper.readValue(is, java.util.Map.class);
                    if (map.containsKey("plugin")) {
                        java.util.Map<String, Object> pMap = (java.util.Map<String, Object>) map.get("plugin");
                        pMap.put("componentType", "plugin");
                        return new MetaObject(pMap);
                    }
                    map.put("componentType", "plugin");
                    return new MetaObject(map);
                }
            }
        }
    }

    private boolean isValid(MetaObject mObj) {
        return mObj != null && mObj.getName() != null && mObj.getImplementationClassName() != null;
    }

    public Set<String> scanLocalFolder() {
        File folder = new File(pluginDir);
        File[] files = folder.listFiles();
        if (files == null) return Collections.emptySet();
        
        Set<String> names = new HashSet<>();
        for (File f : files) names.add(f.getName());
        return names;
    }

    /**
     * Unpacks a Plugin Fat JAR into the active plugin directory.
     * This follows the architecture requirement of local extraction before loading.
     */
    public void unpackPlugin(File jarFile, File targetDir) throws Exception {
        if (!targetDir.exists()) targetDir.mkdirs();
        
        try (ZipFile zip = new ZipFile(jarFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File entryDestination = new File(targetDir, entry.getName());
                
                if (entry.isDirectory()) {
                    entryDestination.mkdirs();
                } else {
                    entryDestination.getParentFile().mkdirs();
                    try (InputStream in = zip.getInputStream(entry);
                         OutputStream out = new FileOutputStream(entryDestination)) {
                        in.transferTo(out);
                    }
                }
            }
        }
        logger.info("Plugin Unpacked: " + jarFile.getName() + " -> " + targetDir.getAbsolutePath());
    }
}
