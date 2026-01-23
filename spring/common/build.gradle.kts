plugins {
    id("spring-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-security") // 'api' makes it visible to consumers
    implementation(project(":reveila")) {
        // slf4j-simple is excluded to prevent conflicts with Spring Boot's logback-classic logging setup
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }
}

tasks.named("bootJar") {
    enabled = false
}

tasks.named("jar") {
    enabled = true
}
