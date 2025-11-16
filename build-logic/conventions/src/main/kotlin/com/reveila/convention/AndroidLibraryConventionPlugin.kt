package com.reveila.convention

    import org.gradle.api.Plugin
    import org.gradle.api.Project

    class AndroidLibraryConventionPlugin : Plugin<Project> {
        override fun apply(project: Project) {
            with(project) {
                pluginManager.apply("com.android.library")
                pluginManager.apply("org.jetbrains.kotlin.android")

                // Configure Android library settings
                // ...
            }
        }
    }