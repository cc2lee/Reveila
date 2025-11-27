// Reveila-Suite/settings.gradle.kts

rootProject.name = "Reveila-Suite"

// 1. Configure Plugin Management (essential for build-logic)

pluginManagement {

    // Define where Gradle can find standard plugins and the foojay resolver
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    
    plugins {
        // Recommended for robust Java toolchain resolution
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    }

    // 2. Include the build-logic module as a composite build
    includeBuild("build-logic")
}

// 3. Declare all other modules in your main project structure

include(":adapters")
include(":apps")
include(":reveila")
include(":spring")
include(":standalone")
// include(":web")
// include(":Compose Multiplatform")
