plugins {
    base // Applying 'base' adds standard tasks like 'assemble', 'clean', and 'build'
    alias(libs.plugins.node)
}

node {
    download.set(true)
    version.set("22.12.0") 
}

val buildVue = tasks.register<com.github.gradle.node.npm.task.NpmTask>("buildVue") {
    dependsOn(tasks.named("npmInstall"))
    args.set(listOf("run", "build-only"))
    
    inputs.dir("src")
    inputs.file("package.json")
    inputs.file("package-lock.json")
    outputs.dir("dist")
}

tasks.named("assemble") {
    dependsOn(buildVue)
}
