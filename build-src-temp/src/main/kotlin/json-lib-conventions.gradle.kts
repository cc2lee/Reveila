// buildSrc/src/main/kotlin/json-lib-conventions.gradle.kts
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

// Note that, the filename "json-lib-conventions" becomes the Plugin ID.

// 1. Access the version catalog
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

// 2. Define the shared logic
dependencies {
    // Apply individual libraries or entire bundles from TOML
    implementation(libs.findLibrary("jackson-xml").get())
    implementation(libs.findBundle("jackson").get())
}

/* To use this convention plugin in any module:
e.g. in spring/reveila/build.gradle.kts

plugins {
    id("json-lib-conventions")
}

// No need to list Jackson dependencies here anymore!

*/