// C:\IDE\Projects\Reveila-Suite\apps\Aegin\build.gradle.kts
import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.play.publisher)
    id("reveila-app-packager")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        f.inputStream().use { load(it) }
    }
}

fun resolveReleaseSetting(propName: String, envName: String): String? {
    return (findProperty(propName) as? String)?.trim()?.takeIf { it.isNotEmpty() }
        ?: localProps.getProperty(propName)?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getenv(envName)?.trim()?.takeIf { it.isNotEmpty() }
}

// ===========================================================================
// SELECTIVE CAPABILITY ASSEMBLY & SIZE OPTIMIZATION (reveila-app-packager)
// Only packs the exact SystemComponents, classes, and configs needed by Aegin.
// ===========================================================================
reveilaApp {
    requiredComponents.set(
        listOf(
            "AgenticFabric",
            "AutonomousAgent",
            "OrchestrationService",
            "KnowledgeVault",
            "HttpClientService",
            "LlmProviderFactory",
            "ClusterService",
            "remote.reveila"
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

        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }

        val desktopMain by getting {
            dependsOn(jvmSharedMain)
            resources.srcDir(layout.buildDirectory.dir("generated/resources/desktopMain"))
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                // Consumes the bytecode-sliced Reveila Core JAR instead of the full capability suite
                implementation(files(tasks.named("sliceCapabilityJars")))
            }
        }

        androidMain {
            dependsOn(jvmSharedMain)
            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.work.runtime.ktx)
                implementation(libs.kotlinx.coroutines.android)
                // Consumes the bytecode-sliced Reveila Core JAR instead of the full capability suite
                implementation(files(tasks.named("sliceCapabilityJars")))
            }
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
        versionCode = resolveReleaseSetting("aeginVersionCode", "AEGIN_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = resolveReleaseSetting("aeginVersionName", "AEGIN_VERSION_NAME") ?: "1.0.0"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }

        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                    "-DGGML_NATIVE=OFF",
                    "-DGGML_OPENMP=OFF",
                    "-DGGML_LLAMAFILE=OFF",
                    "-DLLAMA_CURL=OFF",
                    "-DLLAMA_OPENSSL=OFF",
                    "-DLLAMA_BUILD_TOOLS=OFF",
                    "-DBUILD_SHARED_LIBS=OFF"
                )
                cppFlags("-std=c++17", "-O3", "-fexceptions", "-frtti")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/assets/androidMain"))

    val releaseKeystorePath = resolveReleaseSetting("aegin.keystore.file", "AEGIN_KEYSTORE_FILE")
    val releaseKeystorePassword = resolveReleaseSetting("aegin.keystore.password", "AEGIN_KEYSTORE_PASSWORD")
    val releaseKeyAlias = resolveReleaseSetting("aegin.key.alias", "AEGIN_KEY_ALIAS")
    val releaseKeyPassword = resolveReleaseSetting("aegin.key.password", "AEGIN_KEY_PASSWORD")
    val hasReleaseSigning = !releaseKeystorePath.isNullOrBlank() &&
        !releaseKeystorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // Aggressive R8 code shrinking, obfuscation & resource shrinking for minimal mobile/wearable APK size
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                layout.buildDirectory.file("reveila-slim/reveila-keep.pro").get().asFile
            )
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
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

play {
    val serviceAccountPath = resolveReleaseSetting("aegin.play.serviceAccountJson", "AEGIN_PLAY_SERVICE_ACCOUNT_JSON")
    if (!serviceAccountPath.isNullOrBlank()) {
        serviceAccountCredentials.set(rootProject.file(serviceAccountPath))
    }
    track.set(resolveReleaseSetting("aeginPlayTrack", "AEGIN_PLAY_TRACK") ?: "internal")
    val statusName = (resolveReleaseSetting("aeginPlayReleaseStatus", "AEGIN_PLAY_RELEASE_STATUS") ?: "DRAFT").uppercase()
    releaseStatus.set(
        when (statusName) {
            "COMPLETED" -> ReleaseStatus.COMPLETED
            "IN_PROGRESS" -> ReleaseStatus.IN_PROGRESS
            "HALTED" -> ReleaseStatus.HALTED
            else -> ReleaseStatus.DRAFT
        }
    )
    defaultToAppBundles.set(true)
}

compose.desktop {
    application {
        mainClass = "com.reveila.aegin.MainKt"

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
    dependsOn(stageAndroidSlimAssets, "sliceCapabilityJars", "generateReveilaKeepRules")
}

tasks.matching {
    it.name != "stageDesktopSlimResources" &&
        it.name != "stageAndroidSlimAssets" &&
        (it.name == "desktopProcessResources" ||
            it.name.contains("Proguard") ||
            it.name.contains("R8") ||
            it.name.contains("Minify"))
}.configureEach {
    dependsOn(stageDesktopSlimResources, "sliceCapabilityJars", "generateReveilaKeepRules")
}

/**
 * Packages a lightweight, portable Windows / Desktop release distribution containing ONLY
 * the sliced Reveila Core JAR, Aegin desktop classes, required runtime libraries, and slim system-home.
 */
val packageSlimWindowsDist by tasks.registering(Sync::class) {
    group = "reveila-packaging"
    description = "Packages a slim, portable Windows distribution with assembled capability artifacts."
    dependsOn("desktopJar", "sliceCapabilityJars", "prepareSlimSystemHome")

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
