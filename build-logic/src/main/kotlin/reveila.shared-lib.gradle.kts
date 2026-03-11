import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure

plugins {
    id("java-conventions")
}

val libs = versionCatalogs.named("libs")

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.findVersion("java-shared").get().toString()))
    }
}
