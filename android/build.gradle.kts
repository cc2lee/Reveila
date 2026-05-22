// C:\IDE\Projects\Reveila-Suite\android\build.gradle.kts
// To build from this directory: ./gradlew :android:assembleRelease or ./gradlew :android:assembleDebug

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "2.1.20-2.0.1"
}

// Modern Gradle service injection for the 'exec' replacement
interface InjectedExec {
    @get:Inject val execOperations: ExecOperations
}

android {
    // "namespace" must match the package of ReveilaService, 
    // which is defined as a service in AndroidManifest.xml 
    // using relative naming.
    namespace = "com.reveila.android"
    compileSdk = 35 

    // Natively attach the Java 17 Toolchain to the Android execution pipeline
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    defaultConfig {
        minSdk = 26
        lint.targetSdk = 35
        buildConfigField("String", "REVEILA_PLATFORM", "\"ANDROID\"")
        buildConfigField("String", "REVEILA_PROPERTIES_URL", "\"\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        compose = false 
    }
}

dependencies {
    // ---------------------------------------------------------------------------
    // EXPO WORKSPACE COMPATIBILITY LAYER:
    // If compiled from the root suite workspace, link directly to the live core project.
    // If isolated by Expo, fall back to the pre-compiled staged asset libs.
    // ---------------------------------------------------------------------------
    if (project.rootProject.name == "Reveila-Suite") {
        implementation(project(":reveila:core"))
    } else {
        implementation(fileTree(mapOf("dir" to "src/main/assets/reveila/system/libs", "include" to listOf("*.jar"))))
    }

    // Use compileOnly because the React Native runtime is supplied by the host mobile shell container
    compileOnly("com.facebook.react:react-android:0.74.1")
    
    // Core Infrastructure Dependencies
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.commonmark:commonmark:0.22.0")
    
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    
    // Persistence (Room Storage Fabric)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("androidx.documentfile:documentfile:1.0.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

// ---------------------------------------------------------------------------
// ADVANCED RUNTIME COMPILATION & STAGING TASKS
// These operations only evaluate when executing within the core Suite environment.
// ---------------------------------------------------------------------------
if (project.rootProject.name == "Reveila-Suite") {

    /**
     * Converts standard shared utility JARs into Android DEX format via the d8 compiler.
     */
    val dexSharedLibs = tasks.register("dexSharedLibs") {
        group = "reveila"
        val execOps = project.objects.newInstance<InjectedExec>().execOperations

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
            val androidJar = File(sdkDir, "platforms/android-${android.compileSdk}/android.jar")
            
            libsDir.listFiles { f -> f.extension == "jar" && !f.name.contains("reveila-suite-fat") }?.forEach { jarFile ->
                val outputFile = File(outputDir.get().asFile, jarFile.name)
                println("[Reveila] Dexing ${jarFile.name} -> ${outputFile.absolutePath}")
                
                execOps.exec {
                    commandLine(d8Executable.absolutePath, "--output", outputFile.absolutePath, 
                        if (androidJar.exists()) "--lib" else "", 
                        if (androidJar.exists()) androidJar.absolutePath else "", 
                        jarFile.absolutePath)
                }
            }
        }
    }

    /**
     * Aggregates configuration trees and dexed artifacts straight into the Android asset structure.
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
                include("configs/**", "plugins/**", "resources/**")
                exclude("libs/**", "logs/**", "data/**", "temp/**", "**/.gitignore", "**/running.lock", "bin/**")
            }
            from(dexSharedLibs) { into("libs") }
        }
        into("src/main/assets/reveila/system")
    }

    /**
     * Captures the compiled native Android Library archives (.aar) 
     * and exports them into the centralized suite workspace outputs folder:
     * Reveila-Suite/build/outputs/android
     */
    val exportAarToHome = tasks.register<Copy>("exportAarToHome") {
        group = "reveila"
        description = "Exports compiled debug and release AARS directly into the root workspace outputs directory"
        
        from(layout.buildDirectory.dir("outputs/aar"))
        into(file("${project.rootDir}/build/outputs/android"))
        include("*.aar")
        
        rename { filename ->
            if (filename.contains("release")) "reveila-android-core.aar"
            else "reveila-android-core-debug.aar"
        }
    }

    // Force asset synchronization to execute seamlessly prior to compiling code variations
    tasks.named("preBuild") {
        dependsOn(prepareAndroidHome)
    }

    // Wrap variant task bindings inside an afterEvaluate hook so they apply 
    // AFTER the Android Gradle Plugin dynamically creates assembleDebug and assembleRelease
    afterEvaluate {
        tasks.findByName("assembleDebug")?.finalizedBy(exportAarToHome)
        tasks.findByName("assembleRelease")?.finalizedBy(exportAarToHome)
    }
}