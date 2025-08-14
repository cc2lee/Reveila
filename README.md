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
java -jar build/libs/reveila.jar
```

The server will start on port 8080 by default.


### Interacting with the Reveila Backend API

Reveila backend provides a generic REST API endpoint. Instead of having a unique URL for every action, the application uses a single endpoint to invoke methods on backend components.

#### API Endpoint

-   **URL**: `/api/components/{componentName}/invoke`
-   **Method**: `POST`
-   **Description**: Invokes a method on a specified backend component.

#### Request Body

The body of the `POST` request must be a JSON object with the following structure:

```json
{
  "methodName": "theMethodToCall",
  "args": [ "argument1", 123, { "some": "object" } ]
}
```

-   `methodName`: The name of the method you want to execute on the component.
-   `args`: An array of arguments to pass to the method.

#### Example: Calling an `EchoService`

Here is a TypeScript example of how to call an `echo` method on a component named `EchoService` using the `fetch` API. You would typically place such logic in a dedicated service file within your React Native project.

```typescript
import Config from './config'; // A simple configuration file

async function invokeEcho(message: string): Promise<string> {
  const componentName = 'EchoService';
  const requestPayload = {
    methodName: 'echo',
    args: [message] // The arguments must be in an array
  };

  try {
    const response = await fetch(`${Config.API_BASE_URL}/api/components/${componentName}/invoke`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      body: JSON.stringify(requestPayload)
    });

    if (!response.ok) {
      // The backend provides structured error messages
      const errorData = await response.json();
      console.error(`API Error: ${response.status}`, errorData.error);
      throw new Error(`Request failed: ${errorData.error || response.statusText}`);
    }

    // The 'echo' method returns a simple string, so we read it as text
    const result = await response.text();
    console.log('Success! Server responded with:', result);
    return result;

  } catch (error) {
    console.error('Failed to invoke component:', error);
    throw error; // Re-throw the error for the caller to handle
  }
}
```

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