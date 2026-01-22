import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("project-conventions") 
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val jvmVersion = libs.findVersion("androidJvmTarget").get().requiredVersion

extensions.configure<LibraryExtension> {
    // 1. SDK Versions
    compileSdk = libs.findVersion("androidCompileSdk").get().requiredVersion.toInt()
    
    defaultConfig {
        minSdk = libs.findVersion("androidMinSdk").get().requiredVersion.toInt()
        targetSdk = libs.findVersion("androidTargetSdk").get().requiredVersion.toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Tells the consuming App to keep necessary Reveila classes
        consumerProguardFiles(layout.projectDirectory.file("consumer-rules.pro"))
    }

    // 2. Release Configuration & R8
    buildTypes {
        getByName("release") {
            // Let the final App (the "Consumer") handle the shrinking.
            // If you shrink the library first, and then the App shrinks it again,
            // you often end up with ClassNotFoundException errors that are nearly impossible to trace.
            isMinifyEnabled = false // Set to true to enable code shrinking
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), 
                layout.projectDirectory.file("proguard-rules.pro")
            )
        }
    }

    // 3. Java Compatibility
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(jvmVersion)
        targetCompatibility = JavaVersion.toVersion(jvmVersion)
    }
}

// 4. Kotlin JVM Target
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(jvmVersion))
    }
}

// 5. Shared Dependencies
dependencies {
    "implementation"(libs.findLibrary("androidx-core-ktx").get())
    "testImplementation"("junit:junit:4.13.2")
}