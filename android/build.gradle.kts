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

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    // Needs ksp for Room compiler
    // ksp(libs.room.compiler)
}

// Ensure the resources are ready before the library starts bundling
tasks.named("preBuild") {
    // Only depend on :prepareAndroidHome if it exists in the current build context
    if (rootProject.tasks.findByName("prepareAndroidHome") != null) {
        dependsOn(":prepareAndroidHome")
    }
}