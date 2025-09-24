plugins {
	java
    id("org.springframework.boot") version "3.5.6"
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

group = "com.reveila"
version = "1.0.0-SNAPSHOT"
description = "Spring Boot with Reveila"

dependencies {

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    
    implementation("com.reveila:reveila:1.0.0-SNAPSHOT")
    runtimeOnly("com.h2database:h2")
    
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