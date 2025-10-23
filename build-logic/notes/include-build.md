In Gradle's settings.gradle.kts, add:

rootProject.name = "your-root-project"

// Include another build located at a relative path
includeBuild("../path/to/included-build") {
    dependencySubstitution {
        // Substitute a specific external module with a project from the included build
        substitute(module("com.example:some-library")).using(project(":some-project-in-included-build"))

        // You can add more substitution rules here
        // substitute(module("another.group:another-artifact")).using(project(":another-project"))
    }
}

// You can include other builds as needed
// includeBuild("another-local-build")

