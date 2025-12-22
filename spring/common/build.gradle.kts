plugins {
    id("spring-conventions")
    //`java-library`
    //alias(libs.plugins.spring.boot).apply(false) // Use the alias from the TOML file and don't apply it to this library
    //alias(libs.plugins.spring.dependency.management).apply(false) // Use the alias from the TOML file and don't apply it to this library
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
