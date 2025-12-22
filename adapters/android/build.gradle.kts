plugins {
    // id("com.reveila.convention.android.app")
    id("com.android.library")
    id("maven-publish")
}

android {
    namespace = "com.reveila.android.adapter"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDir("src/main/kotlin") 
            java.srcDir("src/main/java") 
            resources.srcDir("src/main/resources")
        }
        getByName("test") {
            kotlin.srcDir("src/test/kotlin")
            java.srcDir("src/test/java")
            resources.srcDir("src/test/resources")
        }
    }

    /* NOT WORKING WITH AGP 8.0.0+

    publishing {
        // REQUIRED: Explicitly tell AGP to create the "release" component
        singleVariant("release") {
            // Optional: Include source jars
            // withSourcesJar()
            // Optional: Include javadoc jars
            // withJavadocJar()
        }
    }

    */

}

// Use afterEvaluate as a safeguard for assembly tasks
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = "com.reveila"
                artifactId = "android"
                version = "1.0.0"

                // Manually add the main AAR artifact
                artifact(tasks.getByName("bundleReleaseAar"))
                
                // Optional: Attach sources if needed
                // from(components["release"]) // This line caused the error, so we replace it with manual artifacts

                // If you need sources/javadoc jars, you need these extra tasks:
                /*
                // Task to generate sources JAR
                tasks.register<Jar>("sourcesJar") {
                    archiveClassifier.set("sources")
                    from(android.sourceSets["main"].java.srcDirs)
                }
                artifact(tasks.getByName("sourcesJar"))
                */
            }
        }
        repositories {
            // ... your repository setup ...
            // mavenLocal()
        }
    }
}

dependencies {

    // My dependencies
    implementation(project(":reveila")) // Add Reveila library as a dependency
    
    // If you have any *.jar files, put them in the libs/ directory.
    // If you have multiple *.jar files, you can use a wildcard:
    // implementation fileTree(dir: "libs", include: ["*.jar"]) // Groovy syntax
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar")))) // Kotlin DSL syntax

    // AndroidX libraries
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
}
