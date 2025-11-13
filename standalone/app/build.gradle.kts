group "com.reveila"
version "1.0.0"
description = "Reveila Standalone Application"

plugins {
    application
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
            groupId = "com.reveila"
            artifactId = "standalone"
            version = "1.0.0"
            from(components["java"])
        }
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

repositories {
    mavenCentral()
}

dependencies {

    implementation("com.reveila:reveila:1.0.0")
    
    // Use JUnit Jupiter for testing.
    testImplementation(libs.junit.jupiter)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // This dependency is used by the application.
    implementation(libs.guava)
}

application {
    // Define the main class for the application.
    mainClass = "com.reveila.standalone.App"
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}
