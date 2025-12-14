group = "com.reveila"
version = "1.0.0"
description = "Reveila - Spring Boot"

plugins {
	java
    id("org.springframework.boot") version "3.5.8" // To update Spring Boot version, also update in Dockerfile
    id("io.spring.dependency-management") version "1.1.7"
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

dependencies {

    implementation("com.reveila:reveila:1.0.0") {
        // slf4j-simple is excluded to prevent conflicts with Spring Boot's logback-classic logging setup
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }
    
    // Spring Boot Admin Client dependency
    implementation("de.codecentric:spring-boot-admin-starter-client:3.5.5")
    implementation("org.springframework.boot:spring-boot-starter-security")
    
    // The Jackson dependency is required for XML/JSON conversion.
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1")

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.apache.httpcomponents.client5:httpclient5")
    
    runtimeOnly("com.h2database:h2")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    archiveClassifier.set("boot")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    jvmArgs = listOf("-Xmx2048m", "-Dspring.profiles.active=dev")
}