plugins {
    id("reveila-runtime")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
}

dependencies {
    api(files("${rootProject.projectDir}/distribution/jvm/reveila-core.jar"))
    
    // AI / Security Runtime dependencies (Server-specific or high-performance)
    implementation(libs.docker.java.api)
    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport.httpclient5)
    
    // AWS SDK (Managed via BOM in parent)
    implementation(platform(libs.aws.sdk.bom))
    implementation(libs.aws.sdk.s3)
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(":reveila:core:exportJvmJar")
}

val exportRtJar by tasks.registering(Copy::class) {
    group = "distribution"
    description = "Exports the compiled runtime jar to distribution/jvm/reveila-rt.jar"
    dependsOn(tasks.named("jar"))
    from(tasks.named<Jar>("jar").map { it.archiveFile })
    into(file("${rootProject.projectDir}/distribution/jvm"))
    rename { "reveila-rt.jar" }
}
