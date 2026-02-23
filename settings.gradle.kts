// Reveila-Suite/settings.gradle.kts

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // This applies the plugin to the settings file itself and the entire build
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        // If a library is available from more than one of the listed repositories, Gradle will simply pick the first one.
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

includeBuild("build-logic") // Include my convention plugins

// Multi-Project Rule: 
// Every subproject must be included in the root settings.gradle.kts via include("project-name"). 
// If you moved a project into a sub-folder but didn't update the include path, Gradle will ignore it.
// MUST specify a custom path if you want the project name to differ from its folder name, 
// or if the project is located outside the root hierarchy.
// Example 1: include(":my-project"); project(":my-project").projectDir = file("path/to/my-project")
// In a flat structure, if you want the project to be named :reveila but its files are physically inside spring/reveila/,
// Example 2: include("reveila"); project(":reveila").projectDir = file("spring/reveila")
// include(":connectors")
include(":reveila")
include(":spring:admin")
include(":spring:core")
include(":standalone")
include(":web:vue-project")
include(":android")
