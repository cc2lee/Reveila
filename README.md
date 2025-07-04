# Spring Boot Gradle Project

This is a simple Spring Boot application built using Gradle. 

## Project Structure

```
spring-boot-gradle-project
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com
│   │   │       └── example
│   │   │           └── Application.java
│   │   └── resources
│   │       └── application.properties
│   └── test
│       └── java
│           └── com
│               └── example
│                   └── ApplicationTests.java
├── build.gradle
├── settings.gradle
└── README.md
```

## Prerequisites

- Java 11 or higher
- Gradle 6.0 or higher

## Setup Instructions

1. Clone the repository:
   ```
   git clone <repository-url>
   ```

2. Navigate to the project directory:
   ```
   cd spring-boot-gradle-project
   ```

3. Build the project:
   ```
   ./gradlew build
   ```

4. Run the application:
   ```
   ./gradlew bootRun
   ```

## Usage

Once the application is running, you can access it at `http://localhost:8080`.

## Running Tests

To run the tests, use the following command:
```
./gradlew test
```

## License

This project is licensed under the MIT License.