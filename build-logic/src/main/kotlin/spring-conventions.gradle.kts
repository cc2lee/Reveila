import org.springframework.boot.gradle.plugin.SpringBootPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

plugins {
    `java-library`
    id("project-conventions") // Apply project-wide conventions
    id("org.springframework.boot")
    // id("io.spring.dependency-management") // Deprecated in favor of Gradle Native Platforms (Spring Boot 4.0+ style)
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.jetbrains.kotlin.plugin.allopen")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

// Use Java version defined in version catalog
extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.findVersion("java").get().toString()))
    }
}

// Preserve parameter names for reflection at runtime
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
    options.encoding = "UTF-8"
}

// Use JUnit Platform for tests
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

dependencies {
    // Import Native Gradle BOM using the plugin's internal coordinates
    "implementation"(platform(SpringBootPlugin.BOM_COORDINATES))
    "testImplementation"(platform(SpringBootPlugin.BOM_COORDINATES))
    //"implementation"(platform(libs.findLibrary("spring-boot-dependencies").get())) // if using version catalog
    "implementation"("org.springframework.boot:spring-boot-starter")
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
}

// Kotlin All-Open compiler plugin configuration. Kotlin classes and methods are final by default.
// This makes classes and methods open if they are annotated with one of the specified annotations.
extensions.configure<AllOpenExtension> {
    annotation("org.springframework.stereotype.Component")
    annotation("org.springframework.transaction.annotation.Transactional")
    annotation("org.springframework.scheduling.annotation.Async")
    annotation("org.springframework.cache.annotation.Cacheable")
}
