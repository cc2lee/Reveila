# JavaScript UI <> ReveilaBridge.js <> ReveilaModule.java

This file path mapping represents the classic **React Native Native Module Bridge architecture** (specifically operating under a monorepo setup). It acts as a clear, type-safe pipeline that spans from your React Native/Expo UI layer, down through a JavaScript wrapper, and directly into your compiled native Java execution layer.

Here is exactly how the communication chain links together across those three files:

---

### Step 1: The UI Layer (`app/(tabs)/index.tsx`)

```typescript
import { ReveilaBridge } from '../../ReveilaBridge';

```

* **What it does:** Your frontend React Native screen needs to call a background operation. Instead of writing platform-specific code directly in the view, it imports a clean JavaScript/TypeScript interface wrapper (`ReveilaBridge`).
* **Why this exists:** This keeps your UI platform-agnostic. The UI doesn't care if it's running on Android or iOS; it just calls methods exposed by `ReveilaBridge`.

---

### Step 2: The JavaScript Bridge Wrapper (`ReveilaBridge.js`)

```javascript
import { NativeModules } from 'react-native';
const { ReveilaModule } = NativeModules;

```

* **What it does:** This file acts as the binding layer. It imports `NativeModules` from the core React Native library. When React Native boots up, the native side registers all available custom modules into this global `NativeModules` object.
* **The Magic Link:** By destructuring `const { ReveilaModule } = NativeModules;`, JavaScript looks inside that global container for a registered native module matching the exact string name `"ReveilaModule"`.
* **Execution:** When you call a method like `ReveilaBridge.initializeEngine()`, this wrapper passes the execution arguments over the React Native asynchronous bridge boundary using JSON-serialized messages.

---

### Step 3: The Native Java Layer (`ReveilaModule.java`)

```java
package com.reveila.android;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

public class ReveilaModule extends ReactContextBaseJavaModule {
    
    // 1. This binds the Java file to the JavaScript string lookup
    @Override
    public String getName() {
        return "ReveilaModule"; 
    }

    // 2. This exposes specific native methods to the bridge
    @ReactMethod
    public void initializeEngine(String configPath) {
        // High-concurrency Java thread execution happens here
    }
}

```

* **The Binding Mechanism:** The connection relies entirely on the overridden `getName()` method in Java. The string returned here (`"ReveilaModule"`) **must perfectly match** the name destructured from `NativeModules` in your `ReveilaBridge.js` file.
* **The Bridge Methods:** Any method inside this Java class marked with the `@ReactMethod` annotation is automatically indexed by the React Native framework during app startup. When JavaScript calls `ReveilaModule.initializeEngine(...)`, the React Native framework intercepts the call, switches threads from the JavaScript thread to the Native runtime thread, and executes the actual underlying Java code.

---

### Summary of the Flow

$$text{UI View (index.tsx)} \xrightarrow{\text{Method Call}} text{JS Wrapper (ReveilaBridge.js)} \xrightarrow{\text{RN NativeModules Lookup}} text{Java Runtime (ReveilaModule.java)}$$

For this entire pipeline to work smoothly during a build, the `ReveilaModule` must be registered inside a `ReactPackage` class (in our case, `ReveilaPackage.java`) which is then added to the host application's package list — since the React Native Auto Linking feature does not support our mono project setup, we need to mannually add `ReveilaModule` inside the `ReactPackage.java` class.

```java
public class ReveilaPackage implements ReactPackage {

    @Override
    public List<NativeModule> createNativeModules(ReactApplicationContext reactContext) {
        return Collections.singletonList(new ReveilaModule(reactContext));
    }

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
        return Collections.singletonList(new ReveilaViewManager());
    }
}
```
