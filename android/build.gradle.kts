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

        // Bundled URL for fetching reveila.properties at startup
        // 10.0.2.2 is the default alias for the host machine in Android Emulator
        buildConfigField("String", "REVEILA_PROPERTIES_URL", "\"http://10.0.2.2:8080/configs/reveila.properties\"")
    }

    buildFeatures {
        // Explicitly enable because it is false by default in AGP 8.0+
        buildConfig = true
    }
}

dependencies {
    implementation(project(":reveila"))
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = "com.reveila.android"
                artifactId = "common"
                version = "1.0.0"
                artifact(tasks.getByName("bundleReleaseAar"))
            }
        }
    }
}
