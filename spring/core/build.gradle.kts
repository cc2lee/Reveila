// This file bridges the world of NPM/Vite and Gradle.

evaluationDependsOn(":web:vue-project")

group = "com.reveila"
version = "1.0.0"
description = "Spring Boot Services"

plugins {
    id("reveila-spring")
}

sourceSets {
    main {
        resources {
            // Tell Spring to include the Vue build output in the JAR's classpath
            srcDir("../../web/vue-project/dist") 
        }
    }
}

// Ensure Vue is built BEFORE Spring processes resources
tasks.named<ProcessResources>("processResources") {
    dependsOn(":web:vue-project:build")
}

dependencies {
    // AWS 1: Import the BOM as a platform dependency
    implementation(platform(libs.aws.sdk.bom))

    // AWS 2: Add specific SDK dependencies without versions
    implementation(libs.aws.sdk.s3)
    //implementation(libs.aws.sdk.lambda)

    api("org.springframework.boot:spring-boot-starter-security") // 'api' makes it visible to consumers
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation(files("${rootProject.projectDir}/distribution/jvm/reveila-rt.jar"))
    implementation(files("${rootProject.projectDir}/distribution/jvm/reveila-core.jar"))

    // Dependencies required by pre-built Reveila Core & Runtime binaries
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.io.core)
    implementation(libs.okhttp)
    implementation(libs.commons.compress)
    implementation(libs.commonmark)
    implementation(libs.jsoup)
    implementation(libs.jspecify)
    implementation(libs.json.schema.validator)
    implementation(libs.docker.java.api)
    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport.httpclient5)
    
    // Spring Boot Admin Client dependency
    implementation("de.codecentric:spring-boot-admin-starter-client:3.5.5")
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
    implementation("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    archiveFileName.set("reveila-suite-fat.jar")
    destinationDirectory.set(file("${project.rootDir}/system-home/standard/libs"))
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    jvmArgs = listOf("-Xmx2048m", "-Dspring.profiles.active=dev")
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(":reveila:rt:exportRtJar")
}



