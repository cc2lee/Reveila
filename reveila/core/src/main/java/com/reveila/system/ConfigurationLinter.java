package com.reveila.system;

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
            }
        }

        // 3. Delegation: Let the Engine handle the Graph Theory
        new DependencyValidator().validate(dependencyMap);
    }
}