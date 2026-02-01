# Spring Env Lookup Hierarchy of Truth

Spring Boot uses a "last one wins" (or more accurately, "highest priority wins") strategy. If you define MONGO_PWD in three different places, Spring will choose the one highest on this list:

Command Line Arguments: e.g., java -jar app.jar --MONGO_PWD=secret (Highest Priority)

Java System Properties: e.g., -DMONGO_PWD=secret

OS/IDE Environment Variables: This is where your IDE settings and OS export commands live.

Application Properties: The values inside your application.properties (Lowest Priority)