1. Core Architectural Strategy

Restructure the mono-repo to support Java 17 for cross-platform and mobile compatibility, and Java 21 for high-concurrency server execution. Use Gradle Toolchains to enforce strict version boundaries.

:reveila:core (Java 17): The "Sovereign Agreement." Contains interfaces, DTOs, and shared safety logic. No Java 21+ syntax allowed.

:reveila:server (Java 21): The "High-Performance Fabric." Implements the engine using Virtual Threads and Spring Boot 3.5.9.

:android (Java 17): The "Mobile Watchdog." Provides the Biometric-gated security bridge for the Super App.

2. Multi-Target Gradle Configuration

gradle/libs.versions.toml Updates:

Ini, TOML

[versions]
java-shared = "17"
java-server = "21"
springBoot = "3.5.9"

[libraries]
# Ensure Scoped Dependencies
reveila-core = { path = ":reveila:core" }
reveila-server = { path = ":reveila:server" }
build-logic Convention Update:
Create two specialized convention plugins:

reveila.shared-lib.gradle.kts: Configures java.toolchain.languageVersion to 17.

reveila.server-app.gradle.kts: Configures java.toolchain.languageVersion to 21.

3. The Biometric Kill Switch Implementation

A. Shared Core (:reveila:core)
Define the immutable safety command:

Java

public record AgentSafetyCommand(
    String agentId,
    SafetyAction action, // HALT, ISOLATE, KILL
    byte[] biometricSignature, 
    long timestamp
) {}
B. Android Implementation (:android)

Implement BiometricSafetyGuard.java.

Flow: When emergencyStopAll() is called, invoke BiometricPrompt.

Upon success, use the Android Keystore to sign a SafetyCommand token.

Pass this token via the React Native bridge to the server.

C. Server Execution (:reveila:server)

Implement a Java 21 safety listener.

Requirement: When a valid Biometric Kill Switch command is received, use a volatile boolean safety flag and Virtual Threads to propagate the stop command across the entire Fabric with sub-millisecond latency.

4. Build & Integration Integrity

withReveila.js: Update the Expo Config Plugin to link only to :android and :reveila:core.

Dependency Guard: Add a check in :android to prevent accidental inclusion of :reveila:server (Java 21 bytecode).

Task Guard: Ensure the :prepareAndroidHome task dependency in :android is guarded with if (tasks.findByName("prepareAndroidHome") != null).