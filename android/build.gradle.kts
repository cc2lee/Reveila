plugins {
    id("android-conventions")
    id("maven-publish")
}

android {
    namespace = "com.reveila.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        
        // The magic for Expo: Ensure we export the BuildKonfig
        buildConfigField("String", "REVEILA_PLATFORM", "\"ANDROID\"")
        buildConfigField("String", "REVEILA_PROPERTIES_URL", "\"\"")
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
}

// Ensure the resources are ready before the library starts bundling
tasks.named("preBuild") {
    // Only depend on :prepareAndroidHome if it exists in the current build context
    if (rootProject.tasks.findByName("prepareAndroidHome") != null) {
        dependsOn(":prepareAndroidHome")
    }
}