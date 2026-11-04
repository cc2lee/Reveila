// reveila/ext/build.gradle.kts
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    id("reveila-project")
}

android {
    namespace = "com.reveila.ext"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // 1. JVM target (Spring Boot / Server / Shared JVM)
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // 2. Android target
    androidTarget {
        publishLibraryVariants("release", "debug")
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // 3. Apple Native targets (iOS & macOS)
    val xcf = XCFramework("ReveilaExt")
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { target ->
        target.binaries.framework {
            baseName = "ReveilaExt"
            xcf.add(this)
        }
    }
    listOf(
        macosX64(),
        macosArm64()
    ).forEach { target ->
        target.binaries.framework {
            baseName = "ReveilaExtMac"
        }
    }

    // 4. Desktop Native targets (Windows & Linux)
    mingwX64 {
        binaries {
            sharedLib {
                baseName = "reveila_ext"
            }
            staticLib {
                baseName = "reveila_ext"
            }
        }
    }
    linuxX64 {
        binaries {
            sharedLib {
                baseName = "reveila_ext"
            }
            staticLib {
                baseName = "reveila_ext"
            }
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(project(":reveila:core"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.io.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmMain.dependencies {
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
        }
    }
}

// ===========================================================================
// DISTRIBUTION / EXPORT TASKS
// Packages and publishes pre-built multiplatform binaries into distribution/
// ===========================================================================
val distributionDir = file("${rootProject.projectDir}/distribution")

val exportJvmJar by tasks.registering(Copy::class) {
    group = "distribution"
    description = "Exports the compiled JVM jar to distribution/jvm/reveila-ext.jar"
    dependsOn(tasks.named("jvmJar"))
    from(tasks.named<Jar>("jvmJar").map { it.archiveFile })
    into(file("${distributionDir}/jvm"))
    rename { "reveila-ext.jar" }
}

val exportExtJar by tasks.registering {
    group = "distribution"
    description = "Alias for exporting reveila-ext.jar"
    dependsOn(exportJvmJar)
}

val exportAndroidAar by tasks.registering(Copy::class) {
    group = "distribution"
    description = "Exports the compiled Android AAR to distribution/android/reveila-ext.aar"
    dependsOn(tasks.named("bundleReleaseAar"))
    from(layout.buildDirectory.file("outputs/aar/ext-release.aar"))
    into(file("${distributionDir}/android"))
    rename { "reveila-ext.aar" }
}

val exportWindowsBinaries by tasks.registering(Copy::class) {
    group = "distribution"
    description = "Exports the compiled Windows native DLL and C header to distribution/windows/"
    dependsOn(tasks.named("linkReleaseSharedMingwX64"))
    from(layout.buildDirectory.dir("bin/mingwX64/releaseShared")) {
        include("reveila_ext.dll", "reveila_ext_api.h", "reveila_ext.def")
    }
    into(file("${distributionDir}/windows"))
}

val exportBinaries by tasks.registering {
    group = "distribution"
    description = "Exports all pre-built multiplatform binaries to distribution/"
    dependsOn(exportJvmJar, exportAndroidAar, exportWindowsBinaries)
}
