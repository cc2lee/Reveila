# ADR 0008: Android Native Service & Universal Mobile App Architecture

## Status
Accepted / Implemented

## Context & Architectural Motivation
The Reveila platform operates as a decentralized, sovereign runtime that can run across desktops, servers, and mobile devices. On Android, Reveila runs as a native Android **Foreground Service** (`ReveilaService`) hosting the Kotlin Multiplatform (KMP) `Reveila` core engine. 

To enable rapid application manufacturing by AI code agents and mobile developers, the architecture decouples the background engine runtime from the user interface. Any mobile application built with React Native / Expo (or native Android UI) communicates with the core engine through a single, protocol-agnostic universal bridge interface:

```javascript
invoke: async (component, method, params = []) => {
    const envelope = JSON.stringify({ component, method, params });
    const responseString = await ReveilaModule.sendCommand(envelope);
    return JSON.parse(responseString);
}
```

This design allows developers or autonomous AI agents to add any new functionality to the UI and implement a corresponding service method in the core engine that runs independently in the background service.

---

## 1. Architecture Overview

```mermaid
flowchart TD
    subgraph UI_Layer ["Expo / JavaScript Applications"]
        UI["React Native UI / Manufactured Apps"]
        Bridge["ReveilaBridge.js (Universal Interface)"]
        UI -->|invoke(component, method, params)| Bridge
    end

    subgraph Native_Bridge ["Android Native Bridge"]
        RNModule["ReveilaModule.java (@ReactMethod sendCommand)"]
        Bridge -->|JSON Envelope| RNModule
    end

    subgraph OS_Service ["Android Background Service"]
        Service["ReveilaService.java (Foreground Service)"]
        Adapter["AndroidPlatformAdapter.java"]
        RNModule -->|engine.invoke(comp, method, params)| Service
        Service --> Adapter
    end

    subgraph Core_Engine ["Reveila Core Engine (KMP)"]
        Reveila["Reveila.kt Engine Runtime"]
        Components["DataService | OrchestrationService | AgenticFabric | UiController | etc."]
        Plugins["Dynamic Plugins (EchoService, etc.)"]
        Adapter --> Reveila
        Reveila --> Components
        Reveila --> Plugins
    end
```

### Component Roles

1. **Expo / React Native UI (`apps/expo/Reveila`)**:
   - Renders interactive screens (Sovereign Matrix chat, Settings, Explore, Modals).
   - Completely decoupled from direct native engine code.
   - All state mutations and queries route through `ReveilaBridge`.

2. **Universal Bridge (`ReveilaBridge.js` & `ReveilaBridge.d.ts`)**:
   - Universal JSON-RPC gateway across the React Native JavaScript-to-Java boundary.
   - Provides liveness polling, dynamic ES6 component proxies, service lifecycle controls, and native capabilities (biometrics, secure storage, emergency stop).

3. **Native Android Module (`ReveilaModule.java`)**:
   - Thin, high-throughput React Native bridge module (`@ReactMethod sendCommand`).
   - Deserializes JSON envelopes on a dedicated background worker pool (`ExecutorService`).
   - Executes calls through `engine.invoke(component, method, params, "127.0.0.1", subject)` with authorized system principals (`system`, `admin`, `ui-client`).

4. **Android Native Service (`ReveilaService.java`)**:
   - Android `Foreground Service` providing high-priority background execution compliance.
   - Extracts workspace assets (configs, schemas, dexed jars) into local flash storage.
   - Initializes `AndroidPlatformAdapter` and manages local GGUF LLM execution via `LocalLlmServer`.

5. **Platform Adapter (`AndroidPlatformAdapter.java`)**:
   - Concrete implementation of `PlatformAdapter` binding the KMP core engine to Android platform primitives.
   - Routes repository storage to Room/SQLite (`RoomRepository`), logging to Android Logcat (`PlatformLogger`), and cryptographic operations to Android Keystore (`AndroidCryptographer`).

---

## 2. System Home Path Resolution & Initialization

In the Reveila Android application, `system.home` is resolved and configured through a multi-tiered pipeline:

### 1. Default Internal Path Resolution
In `AndroidPlatformAdapter.java`, the default path is resolved within the application's sandboxed internal storage:
```java
public static String getSystemHome(Context context) {
    return new File(context.getFilesDir(), ReveilaSetup.TARGET_HOME).getAbsolutePath();
}
```

### 2. Service Initialization (Intent Handling)
In `ReveilaService.java`, the service extracts a custom path from the starting `Intent` if provided:
```java
final String customSystemHome = intent != null ? intent.getStringExtra("systemHome") : null;
if (customSystemHome != null) {
    homePath = customSystemHome;
} else {
    homePath = new File(getFilesDir(), ReveilaSetup.TARGET_HOME).getAbsolutePath();
}
```

### 3. Property Injection
In `ReveilaService.java`, configuration properties are injected into the platform adapter:
```java
Properties props = new Properties();
props.setProperty("platform", "android");
if (systemHome != null) {
    props.setProperty("reveila.system.home", systemHome.getAbsolutePath());
}
PlatformAdapter platformAdapter = new AndroidPlatformAdapter(this, props);
reveila.start(platformAdapter);
```

### 4. Adapter Fallback
In `AndroidPlatformAdapter.java`, default properties are merged prior to initialization:
```java
private static Properties prepareProperties(Context context, Properties overwrites) {
    Properties props = new Properties();
    String defaultHome = new File(context.getFilesDir(), ReveilaSetup.TARGET_HOME).getAbsolutePath();
    props.setProperty(Constants.SYSTEM_HOME, defaultHome);
    if (overwrites != null) {
        props.putAll(overwrites);
    }
    return props;
}
```

### 5. Expo / React Native Bridge
From the JavaScript layer, `ReveilaBridge.startService(systemHome)` initiates the foreground service:
```javascript
ReveilaBridge.startService('/custom/path/override')
    .then(homePath => console.log('Service online at:', homePath));
```

---

## 3. Universal Bridge Specification

### JavaScript Implementation (`ReveilaBridge.js`)
```javascript
// C:\IDE\Projects\Reveila-Suite\apps\expo\Reveila\ReveilaBridge.js
import { NativeModules, Platform } from 'react-native';

const { ReveilaModule } = NativeModules;

export const ReveilaBridge = {
    /**
     * Starts the long-running Android foreground service container.
     */
    startService: async (systemHome = null) => {
        if (Platform.OS !== 'android' || !ReveilaModule) {
            console.warn('[ReveilaBridge] startService is only supported on Android with native ReveilaModule.');
            return false;
        }
        try {
            return await ReveilaModule.startService(systemHome);
        } catch (e) {
            console.error('[ReveilaBridge Intent Failure]:', e);
            return false;
        }
    },

    /**
     * Safe static liveness check. Queries class-level properties instantly.
     */
    getEngineStatus: async () => {
        if (!ReveilaModule || !ReveilaModule.getEngineStatus) {
            return { initialized: false, status: 'OFFLINE' };
        }
        try {
            return await ReveilaModule.getEngineStatus();
        } catch (e) {
            console.error('[Reveila Engine Status Check Failure]:', e);
            return { initialized: false, status: 'OFFLINE' };
        }
    },

    /**
     * Awaits engine startup readiness before triggering dependent service calls.
     */
    waitForEngine: async (timeoutMs = 30000, pollIntervalMs = 500) => {
        const startTime = Date.now();
        while (Date.now() - startTime < timeoutMs) {
            const status = await ReveilaBridge.getEngineStatus();
            if (status && status.initialized) {
                return true;
            }
            await new Promise((resolve) => setTimeout(resolve, pollIntervalMs));
        }
        return false;
    },

    /**
     * Primary JSON Gateway Router. Serializes complex payload properties
     * straight down to the thin Java routing kernel.
     */
    invoke: async (component, method, params = []) => {
        if (!ReveilaModule || !ReveilaModule.sendCommand) {
            throw new Error(`[ReveilaBridge] Native ReveilaModule is unavailable on this platform (${Platform.OS}). Ensure the Android ReveilaService is running.`);
        }
        const normalizedParams = Array.isArray(params) ? params : [params];
        const envelope = JSON.stringify({ component, method, params: normalizedParams });
        const responseString = await ReveilaModule.sendCommand(envelope);
        try {
            return typeof responseString === 'string' ? JSON.parse(responseString) : responseString;
        } catch {
            return responseString;
        }
    },

    /**
     * Dynamic component proxy generator.
     * Enables AI code agents and UI components to invoke any service dynamically:
     *   const agentic = ReveilaBridge.createProxy('AgenticFabric');
     *   const answer = await agentic.askAgent(prompt, sessionId);
     */
    createProxy: (componentName) => {
        return new Proxy({}, {
            get: (_, methodName) => {
                return async (...args) => {
                    return ReveilaBridge.invoke(componentName, methodName, args);
                };
            }
        });
    },

    // Native security and hardware capabilities
    authenticateBiometric: async () => ReveilaModule?.authenticateBiometric?.() ?? false,
    saveSensitiveData: (data) => ReveilaModule?.saveSensitiveData?.(data),
    triggerEmergencyStop: async () => ReveilaModule?.triggerEmergencyStop?.() ?? false
};

export default ReveilaBridge;
```

### TypeScript Typings (`ReveilaBridge.d.ts`)
```typescript
export interface EngineStatus {
    initialized: boolean;
    status: 'ONLINE' | 'INITIALIZING_CORE' | 'OFFLINE' | string;
}

export interface ReveilaBridgeType {
    startService: (systemHome?: string | null) => Promise<string | boolean>;
    getEngineStatus: () => Promise<EngineStatus>;
    waitForEngine: (timeoutMs?: number, pollIntervalMs?: number) => Promise<boolean>;
    invoke: <T = any>(component: string, method: string, params?: any[]) => Promise<T>;
    createProxy: <T = any>(componentName: string) => T;
    authenticateBiometric: () => Promise<boolean>;
    saveSensitiveData: (data: string) => void;
    triggerEmergencyStop: () => Promise<boolean>;
}

export declare const ReveilaBridge: ReveilaBridgeType;
export default ReveilaBridge;
```

---

## 4. Developer Guide: Manufacturing Android Apps with Reveila

Follow this pattern when manufacturing new applications, features, or autonomous agents using AI code agents or manual development:

### Step 1: Implement the Engine Service in Core (`reveila/core`)
Create or extend a `SystemComponent` in Kotlin Multiplatform. Any public method can be invoked via reflection:

```kotlin
package com.reveila.service

import com.reveila.system.SystemComponent

open class AnalyticsService : SystemComponent() {

    open fun generateReport(timeframe: String, limit: Int): Map<String, Any> {
        logger.info("Generating analytics report for $timeframe (limit: $limit)")
        return mapOf(
            "timeframe" to timeframe,
            "generatedAt" to System.currentTimeMillis(),
            "items" to listOf("Event A", "Event B")
        )
    }

    override fun onStart() {
        logger.info("AnalyticsService active.")
    }

    override fun onStop() {}
}
```

Register the component in `configs/shared/standard.json` (or platform config):
```json
{
    "component": {
        "name": "AnalyticsService",
        "class": "com.reveila.service.AnalyticsService",
        "thread-safe": true
    }
}
```

### Step 2: Invoke the Service from the Mobile UI
In any Expo/React Native screen (`app/...tsx`), call the bridge:

#### Style A: Direct Invocation
```typescript
import { ReveilaBridge } from '@/ReveilaBridge';

const fetchReport = async () => {
    try {
        const report = await ReveilaBridge.invoke('AnalyticsService', 'generateReport', ['weekly', 25]);
        console.log('Report received:', report);
    } catch (error) {
        console.error('Service call failed:', error);
    }
};
```

#### Style B: Dynamic Proxy Syntax (Recommended for Clean Code)
```typescript
import { ReveilaBridge } from '@/ReveilaBridge';

// Automatically routes to ReveilaBridge.invoke('AnalyticsService', 'generateReport', [...])
const analytics = ReveilaBridge.createProxy('AnalyticsService');

const fetchReport = async () => {
    const report = await analytics.generateReport('weekly', 25);
    console.log('Report received:', report);
};
```

### Step 3: Lifecycle Management in App Root (`index.js`)
Ensure the Android service is triggered on startup:
```javascript
import { useEffect } from 'react';
import { Platform } from 'react-native';
import { ReveilaBridge } from './ReveilaBridge';

export function App() {
    useEffect(() => {
        if (Platform.OS === 'android') {
            ReveilaBridge.startService();
        }
    }, []);
    // ...
}
```

### Step 4: Loading State Management in Views
Prevent UI lockups while the background daemon stages assets:
```typescript
const [isReady, setIsReady] = useState(false);

useEffect(() => {
    ReveilaBridge.waitForEngine().then(ready => setIsReady(ready));
}, []);

if (!isReady) {
    return <ActivityIndicator size="large" color="#ff6600" />;
}
```

---

## 5. Multiplatform Interoperability Standards

To ensure pure Java/Android compatibility following the Kotlin Multiplatform refactor:

| Feature / Contract | KMP Standard | Compatibility Rule for Android & Java |
| :--- | :--- | :--- |
| **Security Subject** | `com.reveila.system.Subject` | Do not use `javax.security.auth.Subject`. Populate with `com.reveila.system.RolePrincipal`. |
| **Configuration Properties** | `com.reveila.system.Properties` | Do not cast to `java.util.Properties`. Use `props.getProperty()` / `props.setProperty()`. |
| **Optional Type** | `com.reveila.data.Optional` | Multiplatform equivalent of `java.util.Optional`. Supports `.of()`, `.empty()`, `.isPresent()`. |
| **Cryptography** | `DefaultCryptographer` | Use `@JvmOverloads` constructor and `@JvmStatic` companion helpers (`wrapKeyToBase64`, `generateRandomKey`). |
| **Component Properties** | `SystemComponent`, `AbstractComponent` | Exposed via `@JvmField context` and `@JvmField logger` along with `getContext()` and `getLogger()`. |
| **Data Repository** | `Repository<T, ID>` | `search(requestMap)` uses `@JvmSuppressWildcards` on `Map<String, Any>` to match Java method erasure. |
| **Binary Linkage** | Gradle Decoupling | Android consumes `distribution/jvm/reveila-core.jar` with guarded task dependencies on `:reveila:core:exportJvmJar`. |

---

## 6. Build & Verification Commands

### Android AAR Library
```powershell
.\gradlew.bat :android:assembleDebug
```

### Expo Android Native Shell
```powershell
.\gradlew.bat -p apps/expo/Reveila/android tasks
```

### Expo TypeScript Validation
```powershell
cd apps/expo/Reveila
npx tsc --noEmit
```

### Full Standalone Engine Verification (JVM)
```powershell
.\gradlew.bat :apps:java-test:run
```
