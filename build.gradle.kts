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