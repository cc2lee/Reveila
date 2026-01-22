import org.springframework.boot.gradle.plugin.SpringBootPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.the

plugins {
    `java-library`
    id("project-conventions") 
    id("org.springframework.boot")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.jetbrains.kotlin.plugin.allopen")
}

// 1. Correctly access the Version Catalog in build-logic
val libs = the<LibrariesForLibs>()

// 2. Configure Java Toolchain
extensions.configure<JavaPluginExtension> {
    toolchain {
        // Use .get() to extract the version string from the catalog provider
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
}

// 3. Compiler Parameters (Essential for Spring's @Value and constructor injection)
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
    options.encoding = "UTF-8"
}

// 4. Test Configuration
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// 5. Native BOM Management
dependencies {
    // SpringBootPlugin.BOM_COORDINATES is safer than manual strings because 
    // it automatically stays in sync with the applied Spring Boot plugin version.
    "implementation"(platform(SpringBootPlugin.BOM_COORDINATES))
    "testImplementation"(platform(SpringBootPlugin.BOM_COORDINATES))

    // Use catalog-based starters for consistency
    "implementation"("org.springframework.boot:spring-boot-starter")
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
}

// 6. Kotlin All-Open for Spring Proxies
extensions.configure<AllOpenExtension> {
    annotation("org.springframework.stereotype.Component")
    annotation("org.springframework.transaction.annotation.Transactional")
    annotation("org.springframework.scheduling.annotation.Async")
    annotation("org.springframework.cache.annotation.Cacheable")
    annotation("org.springframework.boot.test.context.SpringBootTest")
}