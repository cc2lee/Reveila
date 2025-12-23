plugins {
	id("spring-conventions")
}

dependencies {
	// Spring Boot Admin Server dependency
	// The major and minor version of Spring Boot Admin should generally match the major and minor version of the Spring Boot.
	// For "Spring Boot 3.5.x", use "Spring Boot Admin 3.5.x".
	// Alternative Kotlin syntax: implementation("de.codecentric", "spring-boot-admin-starter-server", "3.5.5")
    implementation("de.codecentric:spring-boot-admin-starter-server:3.5.5")

    // Spring Boot Web starter dependency
    implementation("org.springframework.boot:spring-boot-starter-web")
}
