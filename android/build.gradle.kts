plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("android-conventions")
    id("maven-publish")
}

android {
    namespace = "com.reveila.android.adapter"
    buildFeatures {
        // Explicitly enable because it is false by default in AGP 8.0+
        buildConfig = true
    }
}

// safeguard for assembly tasks
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
}

/*
// Dexing and deploying to the Host App's assets directory
tasks.register('deployPluginToHost') {
    group = "reveila"
    description = "Compiles plugin, converts to DEX, and moves to Host App assets."

    // 1. Ensure the JAR is built first
    dependsOn 'jar'

    doLast {
        def sdkDir = android.sdkDirectory
        def buildToolsVersion = android.buildToolsVersion
        def d8Path = "${sdkDir}/build-tools/${buildToolsVersion}/d8"
        
        // Input: The compiled JAR from this module
        def inputJar = layout.buildDirectory.file("libs/${project.name}.jar").get().asFile
        
        // Output: The Host App's assets folder
        def hostAssetsDir = file("${project.rootDir}/Android-App/src/main/assets/plugins")
        if (!hostAssetsDir.exists()) hostAssetsDir.mkdirs()
        
        def outputDexJar = new File(hostAssetsDir, "${project.name}.dex.jar")

        println "🚀 Dexing: ${inputJar.name} -> ${outputDexJar.path}"

        // 2. Execute d8 command
        exec {
            commandLine d8Path, 
                "--release", 
                "--output", outputDexJar.absolutePath, 
                inputJar.absolutePath
        }

        println "✅ Plugin deployed to Host App assets."
    }
}

*/