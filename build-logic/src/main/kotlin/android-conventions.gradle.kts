import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// 1. Apply the necessary plugins FIRST so their extensions exist
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("project-conventions")
}

// 2. Setup Version Catalog access
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val jvmVersion = libs.findVersion("androidJvmTarget").get().requiredVersion

// 3. Configure the Android Library Extension
// Because we applied 'com.android.library' above, this block will now work!
extensions.configure<LibraryExtension> {
    compileSdk = libs.findVersion("androidCompileSdk").get().requiredVersion.toInt()
    
    defaultConfig {
        minSdk = libs.findVersion("androidMinSdk").get().requiredVersion.toInt()
        targetSdk = libs.findVersion("androidTargetSdk").get().requiredVersion.toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Tells the consuming App to keep necessary Reveila classes
        consumerProguardFiles(layout.projectDirectory.file("consumer-rules.pro"))
    }

    buildTypes {
        getByName("release") {
            // Recommendation: Set to false for Libraries to avoid "double-shrinking"
            isMinifyEnabled = false 
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), 
                layout.projectDirectory.file("proguard-rules.pro")
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(jvmVersion)
        targetCompatibility = JavaVersion.toVersion(jvmVersion)
    }
}

// 4. Configure Kotlin Compiler
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(jvmVersion))
    }
}

// 5. Shared Dependencies
dependencies {
    "api"(project(":reveila"))
    "implementation"(libs.findLibrary("androidx-core-ktx").get())
    "testImplementation"("junit:junit:4.13.2")
}