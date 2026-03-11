// build-logic/build.gradle.kts
plugins {
    `kotlin-dsl`
}

// Ensure the local build-logic can find its own dependencies
repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Standard library references work directly here
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen)
    implementation(libs.android.gradle.plugin)
    implementation(libs.shadow.gradle.plugin)
    implementation(libs.spring.boot.gradle.plugin)
    
    // THIS is the replacement for the "asProvider" or manual file pathing.
    // It allows your precompiled scripts (*.gradle.kts) to see the 'libs' catalog.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    implementation(libs.jakarta.persistence.api)
    implementation(libs.hibernate.core)
    implementation(libs.commons.compress)
}