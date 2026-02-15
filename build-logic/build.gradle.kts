// build-logic/build.gradle.kts
// build-logic is treated as an independent composite build

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen)
    implementation(libs.android.gradle.plugin)
    implementation(libs.shadow.gradle.plugin)
    implementation(libs.spring.boot.gradle.plugin)
    // This allows 'libs' to be used inside your .gradle.kts convention scripts
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    // For compiling classes using JPA annotations
    implementation(libs.jakarta.persistence.api)
    implementation(libs.hibernate.core)
    
    // Fix for ZipArchiveOutputStream putArchiveEntry error
    implementation(libs.commons.compress)
}