package com.reveila.system

import com.reveila.error.ConfigurationException

open class ConfigurationLinter {

    @Throws(Exception::class)
    open fun lint(metas: Collection<MetaObject>, props: Properties?) {
        val registry = metas.associateBy { it.getName() ?: "" }
        val dependencyMap = mutableMapOf<String, List<String>>()

        for (meta in metas) {
            val name = meta.getName() ?: ""
            val deps = meta.getDependencies()
            if (deps.isEmpty()) {
                continue
            }

            dependencyMap[name] = deps

            for (depName in deps) {
                if (!registry.containsKey(depName)) {
                    throw ConfigurationException(
                        "Missing Dependency: [$name] requires '$depName', but it's not defined."
                    )
                }
            }
        }

        DependencyValidator().validate(dependencyMap)
    }
}
