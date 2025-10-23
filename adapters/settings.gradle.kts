pluginManagement {
    plugins {
        id("com.android.library") version "8.13.0"
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "adapters"

includeBuild("../reveila") {
    dependencySubstitution {
        // Substitute the external module with the local project
        substitute(module("com.reveila:reveila:1.0.0")).using(project(":"))
    }
}

include(":reveila-android")
project(":reveila-android").projectDir = settings.rootDir.resolve("reveila-android")