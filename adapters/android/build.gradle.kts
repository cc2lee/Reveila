group "com.reveila"
version "1.0.0"
description = "Reveila Android Platform Adapter Library"

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "com.reveila.android.adapter"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDir("src/main/kotlin") 
            java.srcDir("src/main/java") 
            resources.srcDir("src/main/resources")
        }
        getByName("test") {
            kotlin.srcDir("src/test/kotlin")
            java.srcDir("src/test/java")
            resources.srcDir("src/test/resources")
        }
    }
}

publishing {
    publications {
        release(MavenPublication) {
            groupId = 'com.reveila'
            artifactId = 'android'
            version = '1.0.0'

            // Tell the publication to use the Android library component
            from components.release // Or components.debug, depending on your build type
        }
    }
    
    repositories { // Define repositories (e.g., Maven Local, remote Maven repository)
        mavenLocal()
    }
}

dependencies {

    // My dependencies
    implementation("com.reveila:reveila:1.0.0") // Add Reveila library as a dependency
    
    // If you have any *.jar files, put them in the libs/ directory.
    // If you have multiple *.jar files, you can use a wildcard:
    // implementation fileTree(dir: "libs", include: ["*.jar"]) // Groovy syntax
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar")))) // Kotlin DSL syntax

    // AndroidX libraries
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
}
