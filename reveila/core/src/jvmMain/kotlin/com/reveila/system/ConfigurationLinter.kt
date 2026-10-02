package com.reveila.system

import java.util.Properties
import com.reveila.error.ConfigurationException

open class ConfigurationLinter {

    @Throws(Exception::class)
    open fun lint(metas: Collection<MetaObject>, props: Properties?) {
        // 1. Map for existence checks and Priority validation
        val registry = metas.associateBy { it.getName() ?: "" }

        // 2. Map for the DependencyValidator (Name -> List of Dependency Names)
        val dependencyMap = HashMap<String, List<String>>()

        for (meta in metas) {
            val name = meta.getName() ?: ""

            // Validate that the specified implementation class actually exists
            val className = meta.getImplementationClassName()
            if (!className.isNullOrBlank()) {
                try {
                    Class.forName(className)
                } catch (e: ClassNotFoundException) {
                    throw ConfigurationException(
                        "❌ Invalid Class: Component [$name] references class '$className', but the class was not found in the classpath."
                    )
                }
            }

            val deps = meta.getDependencies()
            if (deps.isEmpty()) {
                continue
            }

            dependencyMap[name] = deps

            // Validate that every declared dependency exists in the config
            for (depName in deps) {
                if (!registry.containsKey(depName)) {
                    throw ConfigurationException(
                        "❌ Missing Dependency: [$name] requires '$depName', but it's not defined."
                    )
                }
            }
        }

        // 3. Delegation: Let the Engine handle the Graph Theory
        DependencyValidator().validate(dependencyMap)
    }
}
