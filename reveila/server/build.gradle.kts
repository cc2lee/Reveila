plugins {
    id("reveila.server-app")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
}

dependencies {
    implementation(project(":reveila:core"))
    
    // AI / Security Runtime dependencies (Server-specific or high-performance)
    implementation(libs.docker.java.api)
    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport.httpclient5)
    
    // AWS SDK (Managed via BOM in parent)
    implementation(platform(libs.aws.sdk.bom))
    implementation(libs.aws.sdk.s3)
}
