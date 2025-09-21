/*
 * This generated file contains a sample Java library project to get you started.
 * For more details on building Java & JVM projects, please refer to https://docs.gradle.org/8.8/userguide/building_java_projects.html in the Gradle documentation.
 */

plugins {
    // Apply the java-library plugin for API and implementation separation.
    `java-library`
}

repositories {
    /*
    // Add local directory as a Maven repository
    maven {
        url = uri("C:/IDE/Projects/Reveila-Suite/mobile/template/node_modules/react-native") // Use absolute or relative path
    }
    */
    google()
    mavenCentral()
}

group "com.reveila" // Replace with your desired group ID
version "1.0.0-SNAPSHOT" // Replace with your desired version

dependencies {
    // Use JUnit Jupiter for testing.
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")

    // This dependency is exported to consumers, that is to say found on their compile classpath.
    // api(libs.commons.math3)

    // This dependency is used internally, and not exposed to consumers on their own compile classpath.
    // implementation(libs.guava)

    // MongoDB driver
    implementation(platform("org.mongodb:mongodb-driver-bom:5.5.1"))
    implementation("org.mongodb:mongodb-driver-sync")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}
