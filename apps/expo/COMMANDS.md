# Common Commands

## In "expo/Reveila" directory, run one of the following npm commands.

- cd Reveila
- npx expo prebuild # convert to bare workflow to access the native Android project files
- npm run android
- npm run ios # you need to use macOS to build the iOS project - use the Expo app if you need to do iOS development without a Mac
- npm run web
- npx expo prebuild --clean # This deletes existing native directories and then regenerates them
- npm run reset-project # To get a fresh app directory. The old app directory will become app-example.

## Manually start Android emulator

C:\IDE\Android\SDK/emulator/emulator @Medium_Phone

## Display Env in PowerShell

Get-ChildItem -Path Env: 

## Expo dependencies fix

In the folder that contains package.json, run:
npx expo install --fix

Use "npm dedupe" or "yarn install --flat" (if using Yarn).

These commands can help resolve dependency conflicts and consolidate multiple versions of the same package (like glob) into a single, higher version if the package manager determines it's safe to do so.

