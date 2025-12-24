// Execution: Run it for a specific subproject using ./gradlew :app:printProjectVersions
// or for all subprojects at once using ./gradlew printProjectVersions
tasks.register("printProjectVersions") {
    group = "help"
    description = "Displays Java, Gradle, AGP, and Spring Boot versions (Config Cache Safe)."

    // Capture all values safely during the CONFIGURATION phase
    // (These run immediately when Gradle builds the task graph)
    
    val gradleVersion = gradle.gradleVersion
    val javaVersion = "${System.getProperty("java.version")} (${System.getProperty("java.vendor")})"

    // Check for AGP using the classpath at configuration time
    val agpVersion = try {
        val agpClass = Class.forName("com.android.Version")
        agpClass.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION").get(null).toString()
    } catch (e: Exception) { 
        "Not applied (or not found in root classpath)" 
    }

    // Check for Spring Boot using the resolved runtimeClasspath at configuration time
    val springBootVersion = configurations.findByName("runtimeClasspath")
            ?.resolvedConfiguration
            ?.resolvedArtifacts
            ?.find { it.moduleVersion.id.group == "org.springframework.boot" && it.moduleVersion.id.name == "spring-boot-autoconfigure" }
            ?.moduleVersion?.id?.version
            ?: "Not found in runtimeClasspath"


    // Pass only the simple Strings into the Execution phase
    doLast {
        println("\n--- Project Environment Versions ---")
        println("Gradle version: $gradleVersion")
        println("Java version:   $javaVersion")
        println("Android AGP:    $agpVersion")
        println("Spring Boot:    $springBootVersion")
        println("------------------------------------\n")
    }
}
