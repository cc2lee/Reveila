Reveila Platform Modularization (Java 17/21 Hybrid)

Objective:

Modularize the Reveila-Suite to support a "Shared Core" compatible with Java 17 (Android) and "Extended Features" leveraging Java 21 (Server). This involves restructuring the project to use Gradle Toolchains and scoped dependencies.

Tasks:

1. Project Restructuring & Toolchaining
Modify gradle/libs.versions.toml to define two Java versions: java-shared = "17" and java-server = "21".

Update build-logic convention plugins to support Java Toolchains.

Create reveila.java-shared.gradle.kts: Sets java.toolchain.languageVersion to 17.

Create reveila.java-server.gradle.kts: Sets java.toolchain.languageVersion to 21.

2. Dependency Scoping & Code Separation
:reveila:core (New Module): Move the absolute "Lowest Common Denominator" logic here. This module must apply the Java 17 toolchain. No Virtual Threads or Java 21+ syntax allowed.

:reveila:server (New Module): This module will depend on :reveila:core but will apply the Java 21 toolchain. Implement the high-concurrency Flight Recorder and AI heavy-lifting here.

:android: Update this module to depend only on :reveila:core.

3. Implement "Facade" Patterns for Version-Specific Features
In :reveila:core, define interfaces for high-concurrency tasks (e.g., ReveilaExecutor).

In :reveila:server, provide a Java 21 implementation using Virtual Threads (Executors.newVirtualThreadPerTaskExecutor()).

In :android, provide a Java 17 implementation using standard java.util.concurrent or Android WorkManager.

4. Build Integrity & Verification
Ensure the root settings.gradle.kts correctly includes the new :reveila:core and :reveila:server modules.

Update withReveila.js to ensure the Expo build correctly links only to the Java 17 compatible :android and :reveila:core modules.

Run a verification check: Ensure :reveila:server fails to compile if the toolchain is manually set to 17 (due to Java 21 features).