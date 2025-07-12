# Spring Boot Gradle Project

This is a sample Spring Boot application built with Gradle, demonstrating a multi-project setup and a RESTful API. The application includes a `reveila` subproject which provides various utility services.

## Prerequisites

- Java Development Kit (JDK) 17 or later.

## Building the Project

This project uses the Gradle wrapper, so you don't need to have Gradle installed on your system.

To build the project, run the following command from the root directory:

```bash
# For Linux/macOS
./gradlew build

# For Windows
gradlew.bat build
```

This will compile the code, run the tests, and create an executable JAR file in the `build/libs/` directory.

## Running the Application

Once the project is built, you can run the application using the following command:

```bash
java -jar build/libs/spring-boot-gradle-project-0.0.1-SNAPSHOT.jar
```

The server will start on port 8080 by default.

## Configuration

The application can be configured via environment variables. The following variables are supported:

| Variable        | Description                  | Default Value                        |
|-----------------|------------------------------|--------------------------------------|
| `DB_URL`        | The database connection URL. | `jdbc:mysql://localhost:3306/mydb`   |
| `DB_USERNAME`   | The database username.       | `root`                               |
| `DB_PASSWORD`   | The database password.       | `password`                           |

To run the application with custom database settings, you can set these environment variables before launching the JAR file.

**Example (Linux/macOS):**
```bash
export DB_URL=jdbc:mysql://prod-db.example.com:3306/production
export DB_USERNAME=prod_user
export DB_PASSWORD=supersecret
java -jar build/libs/spring-boot-gradle-project-0.0.1-SNAPSHOT.jar
```

**Example (Windows Command Prompt):**
```bash
set DB_URL=jdbc:mysql://prod-db.example.com:3306/production
set DB_USERNAME=prod_user
set DB_PASSWORD=supersecret
java -jar build/libs/spring-boot-gradle-project-0.0.1-SNAPSHOT.jar
```

## API Endpoints

The following endpoints are available under the `/api` base path.

### Echo Service

- **GET /api/echo**
  - Echoes back a message. The `Reveila` subproject mock is used here.
  - **Query Parameter:** `name` (optional, defaults to "World")
  - **Example:**
    ```bash
    curl "http://localhost:8080/api/echo?name=Gemini"
    ```

### Greetings

- **POST /api/greetings**
  - Creates a new greeting.
  - **Example:**
    ```bash
    curl -X POST -H "Content-Type: application/json" -d '{"content":"Hello, REST!"}' http://localhost:8080/api/greetings
    ```

- **PUT /api/greetings/{id}**
  - Updates an existing greeting.
  - **Example:**
    ```bash
    curl -X PUT -H "Content-Type: application/json" -d '{"content":"Updated Greeting"}' http://localhost:8080/api/greetings/123
    ```

- **DELETE /api/greetings/{id}**
  - Deletes a greeting.
  - **Example:**
    ```bash
    curl -X DELETE http://localhost:8080/api/greetings/123
    ```

### File Upload

- **POST /api/upload**
  - Uploads a single file.
  - **Example:**
    ```bash
    curl -X POST -F "file=@./README.md" http://localhost:8080/api/upload
    ```