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
}

// Apply the custom java-conventions plugin
apply(plugin = "java-conventions")

// Apply other necessary plugins imperatively
apply(plugin = "org.springframework.boot")
apply(plugin = "io.spring.dependency-management")
apply(plugin = "org.jetbrains.kotlin.jvm")
apply(plugin = "org.jetbrains.kotlin.plugin.spring")
apply(plugin = "org.jetbrains.kotlin.plugin.allopen")

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
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
