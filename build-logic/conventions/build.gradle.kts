    // build-logic/conventions/build.gradle.kts
    
    plugins {
        `kotlin-dsl`
    }

    gradlePlugin {
        plugins {
            
            create("my-android-app-convention") {
                id = "com.reveila.convention.android.app"
                implementationClass = "com.reveila.convention.AndroidApplicationConventionPlugin"
            }

            create("my-android-lib-convention") {
                id = "com.reveila.convention.android.lib"
                implementationClass = "com.reveila.convention.AndroidLibraryConventionPlugin"
            }

            // ... other plugins
        }
    }

    dependencies {
        // Add any dependencies required by your convention plugins
        implementation(libs.android.gradle.plugin) // Example for Android plugins
        // ...
    }