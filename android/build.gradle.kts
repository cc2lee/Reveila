// Reveila-Suite/android/build.gradle.kts
// Reveila-Suite/android> ./gradlew :android:assembleRelease

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("maven-publish")
}

// Modern Gradle service injection for the 'exec' replacement
interface InjectedExec {
    @get:Inject val execOperations: ExecOperations
}

android {
    namespace = "com.reveila.android.lib"
    compileSdk = 35 

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    api(project(":reveila:core"))

    // This provides WritableMap, Promise, ReactPackage, etc.
    // Use 'compileOnly' because the Expo shell will provide the actual library at runtime.
    compileOnly("com.facebook.react:react-android:0.74.1")
    
    // Core Dependencies
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.commonmark:commonmark:0.22.0")
    
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    
    // Persistence (Room) - 'ksp' will now be resolved correctly
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
}

/**
 * Converts shared library JARs to Android DEX format using the modern ExecOperations.
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
            
            // Modern replacement for deprecated 'exec'
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
            include("configs/**", "plugins/**", "resources/**")
            exclude("libs/**", "logs/**", "data/**", "temp/**", "**/.gitignore", "**/running.lock", "bin/**")
        }
        from(dexSharedLibs) { into("libs") }
    }
    into("src/main/assets/reveila/system")
}

tasks.named("preBuild") {
    dependsOn(prepareAndroidHome)
}