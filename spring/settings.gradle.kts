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
}

rootProject.name = "spring"

/*

Note: The using project(':') is a common pattern for single-project included builds.
It refers to the root project of the included build.
If the reveila directory is a multi-project build and you want to use a specific subproject,
you need to reference it by name, as in:

includeBuild("../reveila") {
    dependencySubstitution {
        // Use `using(project(':sub-project'))` to reference the specific subproject
        substitute(module("com.reveila:reveila:1.0.0")).using(project(":sub-project"))
    }
}

*/

includeBuild("../reveila") {
    dependencySubstitution {
        // Substitute the external module with the local project
        substitute(module("com.reveila:reveila:1.0.0")).using(project(":"))
    }
}
