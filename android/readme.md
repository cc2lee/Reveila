# How to use the adapter with a React Native project

## Create a React Native Bridge Module:
In your Android library module, create a new Java/Kotlin class that extends ReactContextBaseJavaModule.
This class will serve as the bridge between your native Android code and React Native.
Implement methods within this class that expose your native functionality using @ReactMethod annotation. These methods will be callable from your React Native JavaScript code.

## Create a React Package:
In your Android library module, create a new Java/Kotlin class that implements ReactPackage.
Override the createNativeModules method and add an instance of your ReactContextBaseJavaModule to the list of modules returned.
Override the createViewManagers method if your library provides custom UI components.

### Register the package:

In your main React Native project's MainActivity.java or MainApplication.java (depending on your project setup), add an instance of your ReactPackage to the list of packages returned by the getPackages() method.

### Access from React Native:

In your React Native JavaScript code, you can now import and use your native module:

JavaScript

        import { NativeModules } from 'react-native';
        const YourLibraryModule = NativeModules.YourLibraryModule; // 'YourLibraryModule' should match the name returned by getName() in your bridge module
        // Now you can call the methods exposed by your native module
        YourLibraryModule.yourNativeMethod();

****************************************************************************************************************


I have completed the integration of the android into your expo-react-native project. Here's a summary of the changes:

Gradle Configuration:

The android project has been included in apps/expo-react-native/android/settings.gradle.
The android is now a dependency for your my-java-module in apps/expo-react-native/modules/my-java-module/android/build.gradle.
Native Module (Kotlin):

The invokeAsync function has been added to ReveilaExpoModule.kt. It parses a JSON payload and calls the invoke method from the ReveilaService.
Native Module (TypeScript):

The invokeAsync method has been added to the TypeScript interface in ReveilaExpoModule.ts to make it available to your React Native code.
React Native UI:

The index.tsx screen now features a UI with a text input (pre-filled with an example), an "Invoke" button, and a result display area to test the native module integration.


npx expo prebuild --clean && npx expo run:android

Prebuild: your project if the android and ios native directories do not already exist, generating them based on your app.json configuration.
Compile: your project into a debug build using your locally installed Android SDK.
Launch: the app on a connected Android emulator (if one is running) or a physical device. If multiple emulators or devices are available, you may be prompted to select one.