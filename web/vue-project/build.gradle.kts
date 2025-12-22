// This file bridges the world of NPM/Vite and Gradle.

plugins {
    alias(libs.plugins.node)
}

node {
    // 2025 Best Practice: Automatically download and use a specific Node version
    download.set(true)
    version.set("22.12.0") // Current LTS as of late 2024/2025
}

// Map Gradle tasks to NPM commands
val buildTask = tasks.named<com.github.gradle.node.npm.task.NpmTask>("npmInstall")

val buildVue = tasks.register<com.github.gradle.node.npm.task.NpmTask>("buildVue") {
    dependsOn(buildTask)
    args.set(listOf("run", "build"))
    
    // Define outputs for caching and for other modules to consume
    inputs.dir("src")
    inputs.file("package.json")
    inputs.file("package-lock.json")
    outputs.dir("dist")
}