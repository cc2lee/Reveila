package com.reveila.system;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import com.reveila.error.ConfigurationException;

public class ConfigurationLinter {

    public void lint(Collection<MetaObject> metas, Properties props) throws Exception {
        // 1. Map for existence checks and Priority validation
        Map<String, MetaObject> registry = metas.stream()
            .collect(Collectors.toMap(MetaObject::getName, m -> m));

        // 2. Map for the DependencyValidator (Name -> List of Dependency Names)
        Map<String, List<String>> dependencyMap = new HashMap<>();

        for (MetaObject meta : metas) {
            String name = meta.getName();
            List<String> deps = meta.getDependencies();
            if (deps == null || deps.isEmpty()) {
                continue;
            }
            
            dependencyMap.put(name, deps);

            // Validate that every declared dependency exists in the config
            for (String depName : deps) {
                if (!registry.containsKey(depName)) {
                    throw new ConfigurationException(String.format(
                        "❌ Missing Dependency: [%s] requires '%s', but it's not defined.",
                        name, depName));
                }

                // Check for Priority Mismatches
                MetaObject depMeta = registry.get(depName);
                if (meta.getStartPriority() <= depMeta.getStartPriority()) {
                    throw new ConfigurationException(String.format(
                        "❌ Dependency Priority Mismatch: %s (P:%d) starts before dependency %s (P:%d)\n",
                        name, meta.getStartPriority(), depName, depMeta.getStartPriority()));
                }
            }
            // 4. Plugin path validation
            String pluginDir = meta.getPluginDir();
            if (pluginDir != null) {
                if (!pluginDir.startsWith("plugins/")) {
                    throw new ConfigurationException(String.format(
                        "❌ Invalid Plugin Path: [%s] directory '%s' must start with 'plugins/'.",
                        name, pluginDir));
                }

                String systemHome = props.getProperty(Constants.SYSTEM_HOME);
                if (systemHome == null || systemHome.isBlank()) {
                    throw new ConfigurationException("Reveila system property 'system.home' is not set.");
                }

                Path homePath = Path.of(systemHome).toAbsolutePath().normalize();
                Path fullPath = homePath.resolve(pluginDir).toAbsolutePath().normalize();

                // Ensure it doesn't escape the home directory via .. or absolute paths
                if (!fullPath.startsWith(homePath)) {
                    throw new ConfigurationException(String.format(
                        "❌ Security Violation: [%s] plugin directory '%s' must be inside system home.",
                        name, pluginDir));
                }

                if (!Files.exists(fullPath)) {
                    throw new ConfigurationException(String.format(
                        "❌ Missing Plugin Directory: [%s] directory '%s' does not exist at %s.",
                        name, pluginDir, fullPath));
                }

                // Standardized Manifest Check (ADR: Plugin Structure)
                Path manifestPath = fullPath.resolve("plugin-manifest.json");
                if (!Files.exists(manifestPath)) {
                    throw new ConfigurationException(String.format(
                        "❌ Missing Plugin Manifest: [%s] directory '%s' is missing 'plugin-manifest.json'.",
                        name, pluginDir));
                }
            }
        }

        // 3. Delegation: Let the Engine handle the Graph Theory
        new DependencyValidator().validate(dependencyMap);
    }
}