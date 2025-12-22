// build-logic/build.gradle.kts: Configures the build-logic composite build

plugins {
    `kotlin-dsl` // Enable Kotlin support for convention plugins
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal() // Add Gradle Plugin Portal
}

// These are the plugins that our convention plugins will apply.
// They are provided as dependencies to the build-logic project itself.
dependencies {
    
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.0")
    implementation("org.jetbrains.kotlin:kotlin-allopen:2.0.0")

    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.5.8")
    implementation("io.spring.dependency-management:io.spring.dependency-management.gradle.plugin:1.1.7")
    
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.3.0")

    // implementation("com.android.tools.build:gradle:8.7.2") // Commented out to fix build-logic compilation
}
