# Common Commands

These commands should be run in the expo/Reveila directory, the directory that contains package.json.

- **npx expo install expo@latest --fix**  # Installs the latest SDK and automatically updates dependencies, including the `package.json` and `package-lock.json` files and installing the physical packages in `node_modules`. After this command completes, the project's dependencies are installed, aligned, and ready to use.
- **npm dedupe**  # Optimizes the `node_modules` directory structure by removing duplicates
- **npx expo install --fix**  # Fixes project dependencies to ensure compatibily
- **npm audit fix**  # Remediates known**security vulnerabilities** in dependencies
- **npx expo-doctor**  # Check project health (problems)
- **npx expo start**  # Start the dev server
- **npx expo prebuild**  # Converts the project to bare workflow to access the native Android project files
- **npm run android, or npx expo run:android**  # Compile and run Android app
- **npm run ios, npx expo run:ios**  # Compile and run iOS app. macOS is required to build the iOS project - use the Expo app if without a Mac
- **npm run web**  # Launch the Web app
- **npx expo prebuild --clean**  # Deletes existing native directories and then regenerates them
- **npm run reset-project**  # To get a fresh app directory. The old app directory will become app-example.
- **npm i -g expo-cli**  # Updates the global Expo CLI
- **C:\IDE\Android\SDK/emulator/emulator @Medium_Phone**  # Manually starts the Android emulator
- **Get-ChildItem -Path Env:**  # Displays env values in Windows PowerShell
- **npm uninstall -g expo-cli**  # Uninstalls the old global Expo CLI. Then run, **npm cache clean --force** to clear the npm cache.
- **npm list -g *react-native-cli***  # Check if package *react-native-cli* is globally installed.
- **npm root -g,** or **npm config get prefix -g**  # Display **npm** global installation path.
- **npm list -g**  # List npm globally installed packages.

**Comparisons:**

| Command                    | Primary Purpose                                                                            | Scope of Action                                                                                                        | Key Characteristic                                                                             |
| -------------------------- | ------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `npx expo install --fix` | Fix dependencied. Ensures **compatibility** of packages within the Expo ecosystem. | Modifies `package.json` and `package-lock.json` to use Expo-compatible versions.                                   | Specific to Expo/React Native projects; focuses on version compatibility, not security.        |
| `npm audit fix`          | Remediates known**security vulnerabilities** in dependencies.                        | Updates vulnerable packages to the latest*compatible* secure versions in `package.json` and `package-lock.json`. | Focuses solely on security advisories; only performs SemVer-compatible updates by default.     |
| `npm dedupe`             | **Optimizes** the `node_modules` directory structure.                              | Rearranges existing packages to reduce duplication and flatten the dependency tree; does not install new modules.      | Focuses on disk space and efficiency; does not change dependency versions in `package.json`. |
