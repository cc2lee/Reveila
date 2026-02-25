# Spring Property Variable Resolution Path

When Spring encounters a property placeholder `${VARIABLE}`, it searches for the value following a strict order of precedence.

1.  **Command-Line Arguments** (Highest Priority)
    Example: `--system.home=C:/path/to/home`
2.  **JVM System Properties**
    Example: `-Dsystem.home=C:/path/to/home`
3.  **OS Environment Variables**
    Example: `SYSTEM_HOME=C:/path/to/home` (Standard practice in Docker Compose)
4.  **External Property Files**
    Example: `reveila.properties` (Loaded via `@PropertySource` or specified at runtime)
5.  **Internal Property Files** (Lowest Priority)
    Example: `application.properties` (Packaged inside the JAR)
