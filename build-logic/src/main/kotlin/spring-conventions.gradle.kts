// build-logic/src/main/kotlin/spring-conventions.gradle.kts

// 1. Define plugin versions in gradle/libs.versions.toml (version catalog).
// 2. Use versionless id(...) calls in convention plugins.
// 3. The project applying the convention plugin will resolve the version from the version catalog.

plugins {
    `java-library`
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.spring")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of("21"))
    }
}

dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    // required since Spring Framework 5.2 for reflection-based parameter name discovery
    options.compilerArgs.add("-parameters")
}

// Kotlin All-Open compiler plugin configuration. Kotlin classes and methods are final by default.
// This makes classes and methods open if they are annotated with one of the specified annotations.
allOpen {
    annotation("org.springframework.stereotype.Component")
    annotation("org.springframework.transaction.annotation.Transactional")
    annotation("org.springframework.scheduling.annotation.Async")
    annotation("org.springframework.cache.annotation.Cacheable")
}