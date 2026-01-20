# Global Standards

## 1. Architectural Patterns
- Follow **Clean Architecture** principles. Keep Business Logic (Domain) independent of Android Framework (Data/UI).
- Use **SOLID** principles rigorously.
- Prefer **Composition over Inheritance**.

## 2. Dependency & Security
- **Marketplace Compliance:** When writing code for AWS/Azure integrations, prioritize using official SDKs.
- **Secrets:** Never write hardcoded API keys. Use `BuildConfig` or environment variables.
- **Shadowing:** For plugin dependencies, suggest "Shadowing/Relocation" to avoid classpath conflicts with the host app.

## 3. Documentation
- Every public method must have a Javadoc block explaining parameters and return values.
- Use `// TODO:` for temporary hacks, followed by your initials (CL).