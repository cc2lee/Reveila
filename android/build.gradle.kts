plugins {
    id("android-conventions")
    alias(libs.plugins.ksp)
    id("maven-publish")
}

android {
    namespace = "com.reveila.android.lib"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    buildToolsVersion = "35.0.0"

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        
        buildConfigField("String", "REVEILA_PLATFORM", "\"ANDROID\"")
        buildConfigField("String", "REVEILA_PROPERTIES_URL", "\"\"")
    }

    buildFeatures {
        buildConfig = true
        compose = false // Explicitly disabled
    }

    // Security Guard: Prevent Server (Java 21) logic from leaking into the Mobile Engine
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.name.contains("reveila-server") || requested.name == "server") {
                throw GradleException("Security Violation: Android module cannot depend on Java 21 :reveila:server")
            }
        }
    }

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
    // Sovereign Core logic
    api(project(":reveila:core")) 
    
    // React Native Bridge (JSON Communication Layer)
    compileOnly(libs.react.android)
    
    // Native Mobile Capabilities
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.pdfbox.android)
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.commonmark)
    
    // Lifecycle components - Standard KTX only (No Compose UI dependencies)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0") // Swapped to standard KTX ViewModel
    
    // Sovereign Persistence (Room)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Data Serialization
    implementation(libs.bundles.jackson)
    implementation("androidx.documentfile:documentfile:1.0.1")
}

/**
 * Converts shared library JARs to Android DEX format using the d8 tool.
 * This allows the Engine to load plugins dynamically.
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
 * Synchronizes the Android System Home into module assets.
 */
val prepareAndroidHome = tasks.register<Sync>("prepareAndroidHome") {
    dependsOn(dexSharedLibs)
    group = "reveila"
    
    var homeDir = file("${project.projectDir}/../system-home/standard")
    if (!homeDir.exists()) {
        homeDir = file("${project.projectDir}/../../../../system-home/standard")
    }

    if (homeDir.exists()) {
        from(homeDir) {
            include("configs/**")
            include("plugins/**")
            include("resources/**")
            exclude("libs/**")
            exclude("logs/**", "data/**", "temp/**", "**/.gitignore", "**/running.lock", "bin/**")
        }

        from(dexSharedLibs) {
            into("libs")
        }
    }
    
    into("src/main/assets/reveila/system")
}

tasks.named("preBuild") {
    dependsOn(prepareAndroidHome)
}