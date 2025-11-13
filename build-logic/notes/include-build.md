In Gradle's settings.gradle.kts, add:

rootProject.name = "your-root-project"

// Include build located at a relative path

includeBuild("../path/to/included-build") {
    dependencySubstitution {
        
        // Substitute a specific external module with a project from the included build
        substitute(module("com.example:some-library")).using(project(":some-project-in-included-build"))

        // You can add more substitution rules here
        substitute(module("another.group:another-artifact")).using(project(":another-project"))
    }
}

EXAMPLE:

Note: The using project(':') is a common pattern for single-project included builds.
It refers to the root project of the included build.
If the reveila directory is a multi-project build and you want to use a specific subproject,
you need to reference it by name, as in:

includeBuild("../reveila") {
    dependencySubstitution {
        // Use `using(project(':sub-project'))` to reference the specific subproject
        substitute(module("com.reveila:reveila:1.0.0")).using(project(":sub-project"))
    }
}
