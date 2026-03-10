buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // Force the version of commons-compress at the buildscript level
        // to prevent classpath leakage during BootJar packaging.
        classpath("org.apache.commons:commons-compress:1.27.1")
    }
}

allprojects {
    configurations.all {
        resolutionStrategy {
            force("org.apache.commons:commons-compress:1.27.1")
        }
    }
}

plugins {
    // This looks up 'android-library' in the [plugins] section of the TOML
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

tasks.register<Copy>("release") {
    group = "reveila"
    description = "Prepares files for release e.g. normalizing line endings."

    from("./system-home/standard/bin") {
        include("*.sh")
        filter { line -> line.replace("\r", "") }
    }
    
    into("./system-home/standard/bin")
}

/**
 * Synchronizes a clean version of the Android System Home into the module resources.
 * Excludes transient development artifacts like logs, local data, and temp files.
 */
val prepareAndroidHome = tasks.register<Copy>("prepareAndroidHome") {
    group = "reveila"
    description = "Syncs clean Android system-home files to module resources."

    from("system-home/android") {
        // MUST-HAVE: Include configs, plugins, and resources
        include("configs/**")
        include("plugins/**")
        include("resources/**")
        include("libs/**")

        // EXCLUDE: Development artifacts
        exclude("logs/**")
        exclude("data/**")
        exclude("temp/**")
        exclude("**/.gitignore")
        exclude("**/running.lock")
    }
    
    // Target the resources folder that gets bundled into the APK
    into("android/src/main/resources/reveila/system")
    
    // Ensure we overwrite existing files with the latest clean versions
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

/**
 * Synchronizes a clean version of the Standard System Home for server deployment.
 */
val prepareStandardHome = tasks.register<Copy>("prepareStandardHome") {
    group = "reveila"
    description = "Syncs clean Standard system-home files to build directory."

    from("system-home/standard") {
        include("bin/**")
        include("configs/**")
        include("plugins/**")
        include("resources/**")
        include("libs/**")

        exclude("logs/**")
        exclude("data/**")
        exclude("temp/**")
        exclude("**/.gitignore")
    }
    
    into("build/distributions/reveila-system-home")
}

// Hook into the build lifecycle for the android project
project(":android") {
    // We use withType to catch all variants (debug, release, etc.)
    // and configureEach to remain lazy (modern Gradle best practice).
    tasks.withType<com.android.build.gradle.tasks.ProcessAndroidResources> {
        dependsOn(prepareAndroidHome)
    }
}