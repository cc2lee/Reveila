# Welcome to your Expo app 👋

This is an [Expo](https://expo.dev) project created with [`create-expo-app`](https://www.npmjs.com/package/create-expo-app).

## Get started

1. Install dependencies

   ```bash
   npm install
   ```

2. Start the app

   ```bash
   npx expo start
   ```

In the output, you'll find options to open the app in a

- [development build](https://docs.expo.dev/develop/development-builds/introduction/)
- [Android emulator](https://docs.expo.dev/workflow/android-studio-emulator/)
- [iOS simulator](https://docs.expo.dev/workflow/ios-simulator/)
- [Expo Go](https://expo.dev/go), a limited sandbox for trying out app development with Expo

You can start developing by editing the files inside the **app** directory. This project uses [file-based routing](https://docs.expo.dev/router/introduction).

## Get a fresh project

When you're ready, run:

```bash
npm run reset-project
```

This command will move the starter code to the **app-example** directory and create a blank **app** directory where you can start developing.

## Google OAuth Setup

To use the Google Sign-In features with the Android Credential Manager, you must register your application in the Google Cloud Console.

1.  **Create a Project**: Go to the [Google Cloud Console](https://console.cloud.google.com/) and create a new project.
2.  **Configure OAuth Consent Screen**: Set up the consent screen and add the `email` and `profile` scopes.
3.  **Create Credentials**:
    - **Android Client ID**: Create an OAuth client ID for Android. You will need the package name (`com.reveila.android`) and the SHA-1 fingerprint of your signing certificate.
    - **Web Client ID**: Create an OAuth client ID for a Web application. This ID is required by the `signInWithGoogle` method in `index.tsx` to identify the server-side audience.
4.  **Download `google-services.json`**: Place the downloaded file in the root of the `apps/expo/Reveila` directory.
5.  **Update `app.json`**: Ensure the `android.package` matches your registered package name and `googleServicesFile` points to your file.

For more details, see the [Expo Google Authentication Guide](https://docs.expo.dev/guides/google-authentication/).

## Learn more

To learn more about developing your project with Expo, look at the following resources:

- [Expo documentation](https://docs.expo.dev/): Learn fundamentals, or go into advanced topics with our [guides](https://docs.expo.dev/guides).
- [Learn Expo tutorial](https://docs.expo.dev/tutorial/introduction/): Follow a step-by-step tutorial where you'll create a project that runs on Android, iOS, and the web.

## Join the community

Join our community of developers creating universal apps.

- [Expo on GitHub](https://github.com/expo/expo): View our open source platform and contribute.
- [Discord community](https://chat.expo.dev): Chat with Expo users and ask questions.
