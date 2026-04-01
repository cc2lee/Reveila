plugins {
    id("android-conventions")
    id("maven-publish")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.reveila.android.lib"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        
        // The magic for Expo: Ensure we export the BuildKonfig
        buildConfigField("String", "REVEILA_PLATFORM", "\"ANDROID\"")
        buildConfigField("String", "REVEILA_PROPERTIES_URL", "\"\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // Dependency Guard: Prevent Java 21 leakage into Android
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.name.contains("reveila-server") || requested.name == "server") {
                 throw GradleException("Security Violation: Android module cannot depend on Java 21 :reveila:server")
            }
        }
    }

    // This ensures that when the library is built, 
    // it includes the resources from your prepareAndroidHome task
    sourceSets {
        getByName("main") {
            resources.srcDirs("src/main/resources")
            assets.srcDirs("src/main/assets")
        }
    }
}

dependencies {
    // API instead of implementation ensures that frameworks using 
    // this library can "see" the Reveila Core classes.
    api(project(":reveila:core")) 
    
    // React Native Bridge
    compileOnly(libs.react.android)
    
    // Biometric Security
    implementation(libs.androidx.biometric)
    
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.commonmark)
    
    // Lifecycle components to help the engine survive Android backgrounding
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    
    // File system tree parsing
    implementation("androidx.documentfile:documentfile:1.0.1")
}

/**
 * Synchronizes a clean version of the Android System Home into the module resources.
 * Excludes transient development artifacts like logs, local data, and temp files.
 */
val prepareAndroidHome = tasks.register<Sync>("prepareAndroidHome") {
    group = "reveila"
    description = "Syncs clean standard system-home files to Android module assets."

    // Locate system-home/standard relative to this project directory
    // Works for mono-repo (../system-home) and Expo (../../../../system-home)
    var homeDir = file("${project.projectDir}/../system-home/standard")
    if (!homeDir.exists()) {
        homeDir = file("${project.projectDir}/../../../../system-home/standard")
    }

    if (homeDir.exists()) {
        from(homeDir) {
            // MUST-HAVE: Include configs, plugins, and resources
            include("configs/**")
            include("plugins/**")
            include("resources/**")
            include("libs/**")

            // EXCLUDE: Development artifacts
            exclude("logs/**")
            exclude("data/**")
            exclude("temp/**")
            exclude("**/.gitignore")
            exclude("**/running.lock")
            
            // EXCLUDE: Server-only scripts
            exclude("bin/**")
        }
    } else {
        println("[Reveila] Warning: system-home/standard not found at $homeDir")
    }
    
    // Target the assets folder that gets bundled into the Android package
    into("src/main/assets/reveila/system")
}

// Ensure the resources are ready before the library starts bundling
tasks.named("preBuild") {
    dependsOn(prepareAndroidHome)
}
