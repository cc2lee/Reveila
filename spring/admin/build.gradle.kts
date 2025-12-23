plugins {
	java
	id("org.springframework.boot") version "3.5.8"
	id("io.spring.dependency-management") version "1.1.7" // Generally do not need to specify a version here. The boot plugin will manage it.
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	// Spring Boot Admin Server dependency
	// The major and minor version of Spring Boot Admin should generally match the major and minor version of the Spring Boot.
	// For "Spring Boot 3.5.x", use "Spring Boot Admin 3.5.x".
	// Alternative Kotlin syntax: implementation("de.codecentric", "spring-boot-admin-starter-server", "3.5.5")
    implementation("de.codecentric:spring-boot-admin-starter-server:3.5.5")

    // Spring Boot Web starter dependency
    implementation("org.springframework.boot:spring-boot-starter-web")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
