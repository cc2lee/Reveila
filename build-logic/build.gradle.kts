tasks.register("displayRoot") {
    group = "My Custom Tasks"
    description = "A simple callable task to display the Gradle root directory."
    doLast {
        println("Gradle root directory: ${rootDir.absolutePath}")
    }
}