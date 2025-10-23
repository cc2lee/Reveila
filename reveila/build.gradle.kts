group "com.reveila"
version "1.0.0"
description = "Reveila - runtime"

plugins {
    `java-library`
    `maven-publish`
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    // This dependency is required by XmlUtil.java for XML/JSON conversion.
    // It is declared as("implementation" to prevent its API from leaking to consumers.
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.1")

    // Dependencies for the RemoteInvoker component.
    // Jackson is used for JSON serialization/deserialization.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1")

    // OkHttp is a modern, efficient HTTP client for making remote calls.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // This dependency is required for logging. The version is now managed by the
    // Spring Boot BOM for the backend build, but must be explicit for the mobile build.
    implementation("org.slf4j:slf4j-api:2.0.13")

    // SLF4J simple implementation for logging
    implementation("org.slf4j:slf4j-simple:2.0.13")

    // Configure test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
}
