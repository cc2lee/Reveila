pluginManagement {

    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }

    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"

        // Define versions for plugins once here
        // Apply the plugin in each module's build.gradle.kts (without specifying a version)
        // id("com.android.library") version "8.13.0" apply false
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
}
