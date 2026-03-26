buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Force the version of commons-compress at the buildscript level
        // to prevent classpath leakage during BootJar packaging.
        classpath("org.apache.commons:commons-compress:1.27.1")
    }
}

plugins {
    // This looks up 'android-library' in the [plugins] section of the TOML
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

allprojects {
    configurations.all {
        resolutionStrategy {
            force("org.apache.commons:commons-compress:1.27.1")
        }
    }
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
val prepareAndroidHome = tasks.register<Sync>("prepareAndroidHome") {
    group = "reveila"
    description = "Syncs clean standard system-home files to Android module assets."

    from("system-home/standard") {
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
        
        // EXCLUDE: Server-only scripts
        exclude("bin/**")
    }
    
    // Target the assets folder that gets bundled into the Android package
    into("android/src/main/assets/reveila/system")
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
    afterEvaluate {
        // We hook into all tasks that process resources or assets to ensure our home files are synced.
        tasks.matching { 
            (it.name.startsWith("process") && it.name.endsWith("Resources")) ||
            (it.name.startsWith("generate") && it.name.endsWith("Assets")) ||
            (it.name.startsWith("package") && it.name.endsWith("Assets"))
        }.configureEach {
            dependsOn(prepareAndroidHome)
        }
    }
}
