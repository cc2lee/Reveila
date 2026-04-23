plugins {
    id("android-conventions")
    id("maven-publish")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.reveila.android.lib"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    buildToolsVersion = "35.0.0"

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
            java.srcDirs("src/main/java")
            kotlin.srcDirs("src/main/kotlin")
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
    
    // Background Tasks
    implementation(libs.androidx.work.runtime.ktx)
    
    // PDF Parsing
    implementation(libs.pdfbox.android)

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
    
    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Jackson (for JSON serialization in DB fields)
    implementation(libs.bundles.jackson)

    // File system tree parsing
    implementation("androidx.documentfile:documentfile:1.0.1")
}

/**
 * Converts shared library JARs to Android DEX format using the d8 tool.
 */
val dexSharedLibs = tasks.register("dexSharedLibs") {
    group = "reveila"
    description = "Converts system-home/standard/libs JARs to Android DEX format."

    val homeDir = file("${project.projectDir}/../system-home/standard")
    val libsDir = file("${homeDir}/libs")
    val outputDir = layout.buildDirectory.dir("reveila/dex-libs")

    inputs.dir(libsDir)
    outputs.dir(outputDir)

    doLast {
        if (!libsDir.exists()) return@doLast
        outputDir.get().asFile.mkdirs()

        val buildToolsVersion = android.buildToolsVersion
        val sdkDir = android.sdkDirectory
        val d8Executable = File(sdkDir, "build-tools/$buildToolsVersion/d8" + (if (org.apache.tools.ant.taskdefs.condition.Os.isFamily(org.apache.tools.ant.taskdefs.condition.Os.FAMILY_WINDOWS)) ".bat" else ""))

        if (!d8Executable.exists()) {
            throw GradleException("d8 executable not found at: ${d8Executable.absolutePath}")
        }

        val androidJar = File(sdkDir, "platforms/android-${android.compileSdk}/android.jar")
        if (!androidJar.exists()) {
            println("[Reveila] Warning: android.jar not found at ${androidJar.absolutePath}. Dexing may fail.")
        }

        libsDir.listFiles { f -> f.extension == "jar" && !f.name.contains("reveila-suite-fat") }?.forEach { jarFile ->
            val outputFile = File(outputDir.get().asFile, jarFile.name)
            println("[Reveila] Dexing ${jarFile.name} -> ${outputFile.absolutePath}")
            
            exec {
                val args = mutableListOf<String>()
                args.add(d8Executable.absolutePath)
                args.add("--output")
                args.add(outputFile.absolutePath)
                if (androidJar.exists()) {
                    args.add("--lib")
                    args.add(androidJar.absolutePath)
                }
                args.add(jarFile.absolutePath)
                commandLine(args)
            }
        }
    }
}

/**
 * Synchronizes a clean version of the Android System Home into the module resources.
 * Excludes transient development artifacts like logs, local data, and temp files.
 */
val prepareAndroidHome = tasks.register<Sync>("prepareAndroidHome") {
    dependsOn(dexSharedLibs)
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
            // Exclude original libs, we will include the dexed ones
            exclude("libs/**")

            // EXCLUDE: Development artifacts
            exclude("logs/**")
            exclude("data/**")
            exclude("temp/**")
            exclude("**/.gitignore")
            exclude("**/running.lock")
            
            // EXCLUDE: Server-only scripts
            exclude("bin/**")
        }

        // Include the dexed libraries
        from(dexSharedLibs) {
            into("libs")
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
