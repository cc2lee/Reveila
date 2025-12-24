// build-logic/src/main/kotlin/java-conventions.gradle.kts

import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    `java-library`
    id("project-conventions") // Apply project-wide conventions
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

// Use Java version defined in version catalog
extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.findVersion("java").get().toString()))
    }
}

// Preserve parameter names for reflection at runtime
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
    options.encoding = "UTF-8"
}

// Use JUnit Platform for tests
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

dependencies {

    "implementation"(libs.findBundle("slf4j").get()) // logging bundle
    "implementation"(libs.findBundle("jackson").get()) // XML/JSON manipulation
    
    // Configure JUnit dependencies:

    // 1. Import the BOM to manage all JUnit versions
    "testImplementation"(platform(libs.findLibrary("junit.bom").get()))
    
    // 2. Add required modules without specifying versions
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    
    // 3. Optional: add legacy support or the launcher
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

// Optional:
// JaCoCo for test code coverage
// Checkstyle or PMD to maintain a consistent code style
