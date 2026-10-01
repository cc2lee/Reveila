// reveila/core/build.gradle.kts

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    id("reveila-project")
}

android {
    namespace = "com.reveila.core"
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
    // 1. JVM target (Spring Boot / Server / Shared JVM)
    jvm()

    // 2. Android target
    androidTarget {
        publishLibraryVariants("release", "debug")
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // 3. Apple Native targets (iOS & macOS)
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()

    // 4. Desktop Native targets (Windows & Linux)
    mingwX64 {
        binaries {
            sharedLib {
                baseName = "reveila_core"
            }
            staticLib {
                baseName = "reveila_core"
            }
        }
    }
    linuxX64 {
        binaries {
            sharedLib {
                baseName = "reveila_core"
            }
            staticLib {
                baseName = "reveila_core"
            }
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.io.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmMain.dependencies {
            implementation(libs.jackson.databind)
            implementation(libs.jackson.xml)
            implementation(libs.jackson.jsr310)
            implementation(libs.jackson.jdk8)
            implementation(libs.okhttp)
            implementation(libs.slf4j.api)
            implementation(libs.commons.compress)
            implementation(libs.commonmark)
            implementation(libs.jsoup)
            implementation(libs.jspecify)
            implementation(libs.json.schema.validator)
            implementation(libs.org.json)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
        }
    }
}