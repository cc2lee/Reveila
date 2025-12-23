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
}

// Apply shared dependencies to every module using this plugin
dependencies {
    "implementation"(libs.findBundle("slf4j").get()) // logging bundle
    "implementation"(libs.findBundle("jackson").get()) // XML/JSON manipulation
    "testImplementation"(libs.findBundle("junit").get()) // testing bundle
}
