// C:\IDE\Projects\Reveila-Suite\apps\Aegin\build.gradle.kts
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
    id("reveila-app-packager")
}

// ===========================================================================
// SELECTIVE WAREHOUSE PACKAGING & SIZE OPTIMIZATION (reveila-app-packager)
// Only packs the exact SystemComponents, classes, and configs needed by Aegin.
// ===========================================================================
reveilaApp {
    requiredComponents.set(
        listOf(
            "AgenticFabric",
            "OrchestrationService",
            "UiController",
            "KnowledgeVault"
        )
    )
    requiredPlugins.set(emptyList())
    platforms.set(listOf("java", "android"))
    includeLocalLlmBinary.set(
        providers.gradleProperty("includeLocalLlmBinary").map { it.toBoolean() }.orElse(false)
    )
}

val stageAndroidSlimAssets by tasks.registering(Sync::class) {
    group = "reveila-packaging"
    description = "Stages the stripped system-home into Android assets/reveila."
    dependsOn("prepareSlimSystemHome")
    from(layout.buildDirectory.dir("reveila-slim/system-home"))
    into(layout.buildDirectory.dir("generated/assets/androidMain/reveila"))
}

val stageDesktopSlimResources by tasks.registering(Sync::class) {
    group = "reveila-packaging"
    description = "Stages the stripped system-home into Desktop resources/reveila."
    dependsOn("prepareSlimSystemHome")
    from(layout.buildDirectory.dir("reveila-slim/system-home"))
    into(layout.buildDirectory.dir("generated/resources/desktopMain/reveila"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // 1. Android Target (Mobile & Smart Watch ready)
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // 2. Desktop Target (Windows & macOS)
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // 3. iOS Targets
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "AeginUI"
            isStatic = true
        }
    }

    // 4. Web Target (WasmJS)
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("aegin")
        browser {
            commonWebpackConfig {
                outputFileName = "aegin.js"
            }
        }
        binaries.executable()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.io.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        val desktopMain by getting {
            resources.srcDir(layout.buildDirectory.dir("generated/resources/desktopMain"))
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                // Consumes the bytecode-sliced Reveila Core JAR instead of the full fat warehouse
                implementation(files(tasks.named("sliceWarehouseJars")))
            }
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            // Consumes the bytecode-sliced Reveila Core JAR instead of the full fat warehouse
            implementation(files(tasks.named("sliceWarehouseJars")))
        }
    }
}

android {
    namespace = "com.reveila.aegin"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.reveila.aegin"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }

    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/assets/androidMain"))

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // Aggressive R8 code shrinking, obfuscation & resource shrinking for minimal mobile/wearable APK size
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                layout.buildDirectory.file("reveila-slim/reveila-keep.pro").get().asFile
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "**/kotlin-tooling-metadata.json"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "com.reveila.aegin.MainKt"

        // Point Desktop runtime to the slim system-home when running from Gradle
        jvmArgs(
            "-Dsystem.home=${layout.buildDirectory.dir("reveila-slim/system-home").get().asFile.absolutePath}"
        )

        buildTypes.release.proguard {
            isEnabled.set(true)
            optimize.set(true)
            obfuscate.set(false)
            configurationFiles.from(
                project.file("proguard-rules.pro"),
                layout.buildDirectory.file("reveila-slim/reveila-keep.pro")
            )
        }

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "Aegin"
            packageVersion = "1.0.0"
            description = "Aegin Sovereign AI Multiplatform Client"
            vendor = "Reveila"
            modules("java.base", "java.desktop", "java.logging", "java.xml", "jdk.unsupported")
        }
    }
}

// Ensure generated assets, sliced JARs, and ProGuard rules are ready before compilation/packaging
tasks.named("preBuild") {
    dependsOn(stageAndroidSlimAssets, "sliceWarehouseJars", "generateReveilaKeepRules")
}

tasks.matching {
    it.name != "stageDesktopSlimResources" &&
        it.name != "stageAndroidSlimAssets" &&
        (it.name == "desktopProcessResources" ||
            it.name.contains("Proguard") ||
            it.name.contains("R8") ||
            it.name.contains("Minify"))
}.configureEach {
    dependsOn(stageDesktopSlimResources, "sliceWarehouseJars", "generateReveilaKeepRules")
}

/**
 * Packages a lightweight, portable Windows / Desktop release distribution containing ONLY
 * the sliced Reveila Core JAR, Aegin desktop classes, required runtime libraries, and slim system-home.
 */
val packageSlimWindowsDist by tasks.registering(Sync::class) {
    group = "reveila-packaging"
    description = "Packages a slim, portable Windows distribution with sliced warehouse artifacts."
    dependsOn("desktopJar", "sliceWarehouseJars", "prepareSlimSystemHome")

    val distDir = layout.buildDirectory.dir("distributions/aegin-windows-slim")
    into(distDir)

    from(tasks.named("desktopJar")) {
        into("lib")
        rename { "aegin-desktop.jar" }
    }

    from(configurations.named("desktopRuntimeClasspath")) {
        into("lib")
    }

    from(layout.buildDirectory.dir("reveila-slim/system-home")) {
        into("system-home")
    }

    doLast {
        val outDir = distDir.get().asFile
        val batFile = File(outDir, "Aegin.bat")
        batFile.writeText(
            """
            @echo off
            setlocal
            set "APP_HOME=%~dp0"
            start "" javaw -Xmx512m -Dsystem.home="%APP_HOME%system-home" -cp "%APP_HOME%lib\*" com.reveila.aegin.MainKt %*
            """.trimIndent() + "\r\n"
        )
    }
}
