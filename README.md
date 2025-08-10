# Reveila Suite

This repository contains the full Reveila Suite, a multi-project application consisting of a Spring Boot backend and a React Native mobile client.

## Project Structure

The repository is organized as a monorepo with the following key components:

-   `./`: The root contains the Spring Boot application that serves the main API.
-   `./reveila`: A shared Java library subproject used by the Spring Boot backend.
-   `./mobile`: The React Native mobile application.

---

## 1. Backend (Spring Boot)

The backend is a standard Spring Boot application that provides a RESTful API.

### Prerequisites

-   Java Development Kit (JDK) 17 or later.

### Building the Backend

This project uses the Gradle wrapper. To build the project, run the following command from the root directory:

```bash
# For Linux/macOS:
./gradlew build

# For Windows:
gradlew.bat build
```

This will compile the code, run tests, and create an executable JAR file in the `build/libs/` directory.

### Running the Backend

Once built, you can run the application using:

```bash
java -jar build/libs/spring-boot-gradle-project-0.0.1-SNAPSHOT.jar
```

The server will start on port 8080 by default.

### Backend API Endpoints

The following endpoints are available under the `/api` base path:

-   **GET /api/echo**: Echoes back a message.
-   **POST /api/greetings**: Creates a new greeting.
-   **PUT /api/greetings/{id}**: Updates an existing greeting.
-   **DELETE /api/greetings/{id}**: Deletes a greeting.
-   **POST /api/upload**: Uploads a single file.

---

## 2. Mobile Client (React Native)

The mobile application is built with React Native and communicates with the backend service. It features a native Android background service that acts as the core engine. For more details on the mobile architecture, see `mobile/README.md`.

### Prerequisites

-   Node.js and the React Native development environment. Please follow the official setup guide.
-   Android Studio and a configured Android device or emulator.

### Running the Mobile App

Navigate to the `mobile` directory to run the application.

```bash
cd mobile
```

Then, follow these steps:

```bash
# 1. Start the Metro bundler in one terminal
npx react-native start

# 2. In a separate terminal, build and run the Android app
npx react-native run-android
```

The mobile app is configured to communicate with the backend running on `localhost`. Ensure the backend service is running before you test features that require API calls.