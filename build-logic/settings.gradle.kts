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
        id("com.android.library") version "8.13.0" apply false

        // ... define other plugins here ...
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }

    // Include the Reveila build for dependency substitution
    includeBuild("../reveila") { // Adjust project path as needed
        dependencySubstitution {
            // Substitute the external module with the local project
            substitute(module("com.reveila:reveila:1.0.0")).using(project(":"))
        }
    }

}
