group "com.reveila"
version "1.0.0"
description = "Reveila"

plugins {
    id("java-conventions")
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("reveila")
}

dependencies {
    implementation(libs.okhttp)
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.15.2")

    // Docker Java API
    implementation("com.github.docker-java:docker-java:3.3.6")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.3.6")

    // Core module needs access to Spring Core for Cross-Module Tests
    testImplementation(project(":spring:core"))

    // JSON Schema Validation
    implementation("com.networknt:json-schema-validator:1.0.86")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.5.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register("displayRoot") {
    group = "My Custom Tasks"
    description = "A simple callable task to display the Gradle root directory."
    doLast {
        println("Gradle root directory: ${rootDir.absolutePath}")
    }
}
