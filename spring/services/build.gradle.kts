// This file bridges the world of NPM/Vite and Gradle.

evaluationDependsOn(":web:vue-project")

group = "com.reveila"
version = "1.0.0"
description = "Reveila - Spring Boot"

plugins {
    id("spring-conventions")
}

dependencies {
    // AWS 1: Import the BOM as a platform dependency
    implementation(platform(libs.aws.sdk.bom))

    // AWS 2: Add specific SDK dependencies without versions
    implementation(libs.aws.sdk.s3)
    //implementation(libs.aws.sdk.lambda)

    implementation(project(":spring:common"))
    implementation(project(":reveila")) {
        // slf4j-simple is excluded to prevent conflicts with Spring Boot's logback-classic logging setup
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }
    
    // Spring Boot Admin Client dependency
    implementation("de.codecentric:spring-boot-admin-starter-client:3.5.6")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    // Spring Boot Security dependency
    implementation("org.springframework.boot:spring-boot-starter-security")
    
    // The Jackson dependency is required for XML/JSON conversions
    implementation(libs.bundles.jackson)
    
    // Alternatives to spring-boot-starter-web:
    // spring-boot-starter-webflux: For reactive, non-blocking applications.
    // spring-boot-starter-rsocket: For RSocket-based binary protocol
    implementation("org.springframework.boot:spring-boot-starter-web")
    //implementation("org.apache.httpcomponents.client5:httpclient5")
    
    runtimeOnly("com.h2database:h2")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    archiveClassifier.set("boot")
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    jvmArgs = listOf("-Xmx2048m", "-Dspring.profiles.active=dev")
}

// Integrate the Vue.js frontend build into Spring Boot's resources
tasks.named<ProcessResources>("processResources") {
    // Ensure Vue is built before processing resources
    dependsOn(":web:vue-project:buildVue")

    // Copy the Vue 'dist' output into Spring's static folder
    from(project(":web:vue-project").tasks.named("buildVue").map { it.outputs.files.asPath }) {
        into("static")
    }
}
