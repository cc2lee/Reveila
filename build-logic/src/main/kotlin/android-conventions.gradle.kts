import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Plugins required for every Android library module
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Apply the standard Android plugins to every module using this plugin
apply(plugin = "com.android.library")
apply(plugin = "org.jetbrains.kotlin.android")

// Use version catalog to access shared version information
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

// Configure common Android settings for every module using this plugin
extensions.configure<LibraryExtension> {

    compileSdk = libs.findVersion("androidCompileSdk").get().requiredVersion.toInt()
    
    defaultConfig {
        minSdk = libs.findVersion("androidMinSdk").get().requiredVersion.toInt()
        targetSdk = libs.findVersion("androidTargetSdk").get().requiredVersion.toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.findVersion("androidJvmTarget").get().requiredVersion.toInt())
        targetCompatibility = JavaVersion.toVersion(libs.findVersion("androidJvmTarget").get().requiredVersion.toInt())
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = libs.findVersion("androidJvmTarget").get().requiredVersion
    }
}

// Common dependencies for all modules
dependencies {
    "implementation"(libs.findLibrary("androidx-core-ktx").get())
    "testImplementation"("junit:junit:4.13.2")
}