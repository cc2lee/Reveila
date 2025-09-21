pluginManagement { repositories {
    gradlePluginPortal()
} }

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "reveila-bundle"

includeBuild("./spring") {
    dependencySubstitution {
        substitute(module("com.reveila:spring")).using(project(":"))
    }
}

includeBuild("./adapters") {
    dependencySubstitution {
        substitute(module("com.reveila:adapters")).using(project(":"))
    }
}
