pluginManagement {
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
    id("com.android.library") version "8.11.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}

rootProject.name = "reveila-android-adapter"

includeBuild("../../build-logic")
includeBuild("../../reveila") {
    dependencySubstitution {
        substitute(module("com.reveila:reveila:1.0.0")).using(project(":"))
    }
}

