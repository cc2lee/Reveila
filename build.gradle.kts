import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Input
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Force the version of commons-compress at the buildscript level
        // to prevent classpath leakage during BootJar packaging.
        classpath("org.apache.commons:commons-compress:1.27.1")
    }
}

plugins {
    // From the [plugins] section of the TOML
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.ksp) apply false
}

allprojects {
    configurations.all {
        resolutionStrategy {
            force("org.apache.commons:commons-compress:1.27.1")
        }
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion(libs.versions.kotlin.get())
            }
        }
    }
}


tasks.register<Copy>("release") {
    group = "reveila"
    description = "Prepares files for release e.g. normalizing line endings."

    from("./system-home/standard/bin") {
        include("*.sh")
        filter { line -> line.replace("\r", "") }
    }
    
    into("./system-home/standard/bin")
}

// Synchronizing clean Standard System Home for server deployment.
val prepareStandardHome = tasks.register<Copy>("prepareStandardHome") {
    group = "reveila"
    description = "Syncs clean Standard system-home files to build directory."

    from("system-home/standard") {
        include("bin/**")
        include("configs/**")
        include("plugins/**")
        include("resources/**")
        include("libs/**")

        exclude("logs/**")
        exclude("data/**")
        exclude("temp/**")
        exclude("**/.gitignore")
    }
    
    into("build/distributions/reveila-system-home")
}

abstract class EnsureGitHooksPath : ValueSource<String, EnsureGitHooksPath.Params> {
    interface Params : ValueSourceParameters {
        @get:Input
        val repoDir: Property<File>

        @get:Input
        val expectedHooksPath: Property<String>
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val dir = parameters.repoDir.get()
        val expected = parameters.expectedHooksPath.get()

        // Skip silently if not inside a Git checkout (e.g., source archive export)
        if (!File(dir, ".git").exists()) return "NO_GIT_DIR"

        val currentOut = ByteArrayOutputStream()
        val checkResult = execOperations.exec {
            workingDir = dir
            commandLine("git", "config", "--local", "--get", "core.hooksPath")
            standardOutput = currentOut
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }

        val current = currentOut.toString(Charsets.UTF_8).trim()
        if (checkResult.exitValue == 0 && current == expected) {
            return expected
        }

        // Configure --local core.hooksPath only when unset or mismatched
        val setResult = execOperations.exec {
            workingDir = dir
            commandLine("git", "config", "--local", "core.hooksPath", expected)
            isIgnoreExitValue = true
        }

        return if (setResult.exitValue == 0) {
            println("[Reveila-Suite] Configured local Git hooks path -> $expected")
            expected
        } else {
            "GIT_CONFIG_FAILED"
        }
    }
}

// Evaluate during configuration/sync; returns a stable string so Configuration Cache stays valid
providers.of(EnsureGitHooksPath::class) {
    parameters {
        repoDir.set(rootProject.projectDir)
        expectedHooksPath.set(".scripts/git-hooks")
    }
}.get()
