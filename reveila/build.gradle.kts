group "com.reveila"
version "1.0.0"
description = "Reveila"

plugins {
    id("java-conventions")
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("reveila")
}

dependencies {
    implementation(libs.okhttp)
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.15.2")
}

tasks.register("displayRoot") {
    group = "My Custom Tasks"
    description = "A simple callable task to display the Gradle root directory."
    doLast {
        println("Gradle root directory: ${rootDir.absolutePath}")
    }
}
