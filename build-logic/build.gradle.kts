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
    
    // This allows your precompiled scripts (*.gradle.kts) to see the 'libs' catalog.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    // This allows your precompiled scripts to resolve 'org.jetbrains.kotlin.plugin.compose'
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.1.20")

    implementation(libs.jakarta.persistence.api)
    implementation(libs.hibernate.core)
    implementation(libs.commons.compress)
}