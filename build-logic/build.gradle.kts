// build-logic/build.gradle.kts: Configures the build-logic composite build

plugins {
    `kotlin-dsl` // Enable Kotlin support for convention plugins
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal() // Add Gradle Plugin Portal
}

// These are the plugins that the convention plugins will apply.
// They are provided as dependencies to the build-logic project itself.
dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen)
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.spring.dependency.management.plugin)
    
    implementation(libs.shadow.gradle.plugin)
    implementation(libs.android.gradle.plugin) // Added to fix compilation of Android convention plugins
}
