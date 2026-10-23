plugins {
    `java`
    `application`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(files("${rootProject.projectDir}/distribution/jvm/reveila-core.jar"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.io.core)
}

application {
    mainClass.set("com.reveila.test.ReveilaTestApp")
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(":reveila:core:exportJvmJar")
}

tasks.withType<JavaExec>().configureEach {
    standardInput = System.`in`
    workingDir = rootProject.projectDir
    jvmArgs("-Dsystem.home=${rootProject.projectDir}/system-home/standard")
}
