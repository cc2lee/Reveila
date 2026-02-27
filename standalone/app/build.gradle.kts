import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

group "com.reveila"
version "1.0.0"
description = "Reveila Standalone Application"

plugins {
    `java`
    `application`
    `maven-publish`
    id("com.gradleup.shadow") version "9.2.2"
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.reveila"
            artifactId = "standalone"
            version = "1.0.0"
            from(components["java"])
        }
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

repositories {
    mavenCentral()
}

dependencies {

    implementation(project(":reveila"))
    implementation(libs.guava)
    implementation("com.google.guava:guava:33.2.1-jre")

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.reveila.standalone.App")
    applicationDefaultJvmArgs = listOf("-Xmx1G")
}

tasks.withType<JavaExec>().configureEach {

    /*
     *  To pass the URL of reveila.properties as a Gradle's project property, use the following command:
     *  ./gradlew run -PrunArgs="'reveila.properties=file:///your-path-to/reveila.properties' other-args 'another multi-word argument'"
     *  The -P flag is used to pass a project property.
     *  The runArgs property's value will be split by spaces into individual arguments for the main method. Use single quotes for arguments containing spaces.
     *  Here 'args' is an implicit variable, and is never null.
     */
    
    // 1. Check if the 'runArgs' project property is provided on the command line (-P)
    if (project.hasProperty("runArgs")) {
        // If it exists, set the args list to the split value of the property.
        // The 'as String' cast is necessary in Kotlin DSL.
        args = (project.property("runArgs") as String).split(" ")
    }
    // Note: If the property is not set, 'args' starts as an empty list (or whatever default the task has).

    if (false) { // toggle if supplying the reveila.properties URL as command line argument


        // 2. Define the reveila.properties argument to check/add to the args list
        val arg = "reveila.properties=file:///\${system.home}/configs/reveila.properties"

        // 3. Add the reveila.properties argument if it's not already present in the args list
        if (!args.contains(arg)) {
            args(arg) // Use args(arg) to append the argument to the list.
        }
    
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform() // Use JUnit Platform for unit tests.
}

// Create an executable "fat" JAR that includes all dependencies (both external libraries and internal project dependencies)
// Run command: java -jar your-project-1.0-SNAPSHOT-all.jar
// Requires the 'com.gradleup.shadow' plugin applied above, and "import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar"
tasks.getByName<ShadowJar>("shadowJar") {
    // Specify the main class for the executable JAR
    manifest {
        attributes(mapOf("Main-Class" to "com.reveila.standalone.App"))
    }

    // Merges all dependencies into the single JAR file
    // We use `configurations.runtimeClasspath.get()` to access the configuration
    configurations = listOf(project.configurations.runtimeClasspath.get())

    // Optional: Exclude signing files to avoid issues
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

/* No "import" alternative:
tasks.named("shadowJar", com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class.java) {
    manifest {
        attributes(mapOf("Main-Class" to "com.reveila.standalone.App"))
    }
    configurations(project.configurations.runtimeClasspath.get())
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
*/
