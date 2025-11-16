package com.reveila.convention

    import org.gradle.api.Plugin
    import org.gradle.api.Project

    class AndroidApplicationConventionPlugin : Plugin<Project> {
        override fun apply(project: Project) {
            with(project) {
                pluginManager.apply("com.android.application")
                pluginManager.apply("org.jetbrains.kotlin.android")

                // Configure Android application settings
                // ...
            }
        }
    }