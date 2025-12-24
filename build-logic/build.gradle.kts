// build-logic/build.gradle.kts: Configures the build-logic composite build

plugins {
    `kotlin-dsl` // Enable Kotlin support for convention plugins
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen)
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.shadow.gradle.plugin)
    implementation(libs.android.gradle.plugin)
}
