# Add local .jar and .aar files to the build as dependencies

## Add directly in the dependencies { ... } in build.gradle(.kts)

// If you have any *.jar files, put them in the libs/ directory.
// If you have multiple *.jar files, you can use a wildcard:
// implementation fileTree(dir: "libs", include: ["*.jar"]) // Groovy syntax
// implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar")))) // Kotlin DSL syntax

## Use as local repository

repositories {
    flatDir {
        dirs 'libs' // or whatever you named your local JARs folder
    }
}

dependencies {
    implementation name: 'your-dependency-jar-name-without-version-or-extension' // e.g., my-library
}

