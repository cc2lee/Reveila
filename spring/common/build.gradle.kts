plugins {
    id("spring-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-security") // 'api' makes it visible to consumers
}

tasks.named("bootJar") {
    enabled = false
}

tasks.named("jar") {
    enabled = true
}
