Kotlin:

    sourceSets {
        getByName("main") {
            kotlin.srcDir("src/main/kotlin") 
            java.srcDir("src/main/java") 
            resources.srcDir("src/main/resources")
        }
        getByName("test") {
            kotlin.srcDir("src/test/kotlin")
            java.srcDir("src/test/java")
            resources.srcDir("src/test/resources")
        }
    }

Groovy:

    sourceSets {
        main {
            java.srcDirs = ['src/main/java']
        }
    }