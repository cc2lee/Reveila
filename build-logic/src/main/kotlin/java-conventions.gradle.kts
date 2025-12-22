// build-logic/src/main/kotlin/java-conventions.gradle.kts

import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

plugins {
    `java-library`
    id("com.gradleup.shadow")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        // Use Java version defined in version catalog
        languageVersion.set(JavaLanguageVersion.of(libs.findVersion("java").get().toString()))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
    options.encoding = "UTF-8" // global standard
}

// Apply shared dependencies to every module using this plugin
dependencies {
    // Every module gets the SLF4J API for logging
    implementation(libs.findLibrary("slf4j.api").get())

    // Every module gets the core Jackson databind
    implementation(libs.findLibrary("jackson.databind").get())
    
    // Use 'testImplementation' for shared testing tools
    testImplementation(libs.findBundle("junit").get())
}
