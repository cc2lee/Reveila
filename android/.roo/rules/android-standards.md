# Android Standards

## 1. Android Java Rules
- **Dynamic Loading:** Always use `BaseDexClassLoader` subclasses for plugins. Never use `URLClassLoader`.
- **Resources:** All strings must go in `res/values/strings.xml`. No hardcoded UI text.
- **Context Handling:** Avoid static references to `Context` to prevent memory leaks. Use `getApplicationContext()` for long-lived operations.
- **Exception Handling:** Never use empty `catch` blocks. If an exception is ignored, document "Why" in a comment.
