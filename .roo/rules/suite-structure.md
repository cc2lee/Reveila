# Reveila-Suite Integration Rules

## Project Relationships
- **Reveila-Core:** Shared Java logic used by both Backend and Android (if applicable).
- **Java-Backend:** The source of truth for API contracts.
- **Android-App:** Consumes Backend APIs via Retrofit/OkHttp.

## API Consistency
- When editing the Backend, check if the change breaks the Android app's DTOs.
- Always generate/update OpenAPI (Swagger) specs when changing endpoints.
- Use **Shadowing** for third-party libraries in the Android Plugin modules to prevent classpath conflicts.

## Technical Stack
- **Backend:** Spring Boot, Java 21, PostgreSQL.
- **Android:** Kotlin/Java, SDK 34+, DexClassLoader for plugins.
- **Web:** Vue/JavaScript.