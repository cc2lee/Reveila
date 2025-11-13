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

includeBuild("../reveila") {
    dependencySubstitution {
        substitute(module("com.reveila:reveila:1.0.0")).using(project(":"))
    }
}

includeBuild("../standalone") {
    dependencySubstitution {
        substitute(module("com.reveila:standalone:1.0.0")).using(project(":app"))
    }
}
