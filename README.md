# Spring Boot Gradle Project

A service oriented, event driven Java container that allows plug-in of any Java object and exposes its methods as RESTful APIs through simple JSON configuration. It can run standalone or in Docker. It’s designed for rapid service development or integration of stubborn legacy applications. Its lightweight nature also makes it ideal for mobile apps.

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

Copyright (c) 2025 Charles Lee, Reveila LLC

This software is provided for viewing purposes only on GitHub.

You are permitted to:

View the source code on the designated GitHub repository.

You are expressly NOT permitted to:

Use, modify, reproduce, distribute, or create derivative works of this software for any commercial purpose whatsoever.

Use, modify, reproduce, distribute, or create derivative works of this software for any non-commercial purpose without explicit written permission from the copyright holder.

Distribute or sublicense the software or any derivative works.

Remove or alter any copyright or other proprietary notices from the software.

THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

By viewing or accessing this software, you agree to the terms of this license.
