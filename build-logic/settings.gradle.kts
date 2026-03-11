dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            // 'settingsDir' is the build-logic folder. 
            // This ensures it ALWAYS finds the root gradle folder correctly.
            from(files(File(settingsDir.parentFile, "gradle/libs.versions.toml")))
        }
    }
}
