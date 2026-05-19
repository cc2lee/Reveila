// Reveila-Suite/android/settings.gradle.kts

// If this project is being evaluated as part of the root multi-project build,
// it will inherit the root's management block. If VS Code isolates it, this block fires.
if (settings.extensions.findByName("dependencyResolutionManagement") != null) {
    dependencyResolutionManagement {
        versionCatalogs {
            if (findByName("libs") == null) {
                create("libs") {
                    from(files("../gradle/libs.versions.toml"))
                }
            }
        }
    }
}

rootProject.name = "android"