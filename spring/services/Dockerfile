# =====================================================================
# Stage 1: Build Environment - Compiles and packages the application
# =====================================================================
# Use an official Gradle image with JDK 21 and Alpine Linux for size efficiency
FROM gradle:8.7-jdk21-alpine AS builder

# Set the working directory inside the container
WORKDIR /app

# Copy necessary Gradle wrapper files first for better Docker caching
COPY gradlew .
COPY gradlew.bat .
COPY gradle/ gradle/

# Copy the Groovy DSL build configuration files:
# COPY *.gradle ./

# Copy the Kotlin DSL build configuration files:
COPY *.gradle.kts ./

# Copy additional Gradle files
COPY gradle.properties .
COPY gradle/ gradle/

# Download dependencies first to cache them. This layer usually doesn't change often.
RUN gradle dependencies --no-daemon

# Copy the application source code:
COPY src src

# Run the build command to compile code, run tests, and create the JAR/WAR file
# The application artifact will typically be in /app/build/libs/
RUN gradle build --no-daemon -x test 


# =====================================================================
# Stage 2: Runtime Environment - Minimal JRE image for production
# =====================================================================
# Use a minimal JRE image (e.g., from Eclipse Temurin project, based on Alpine)
FROM eclipse-temurin:21-jre-alpine

# Set the working directory in the runtime image
WORKDIR /app

# --- Security Best Practice: Run as non-root user ---
# Create a non-root group and user for security isolation
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser
USER appuser

# Copy the generated JAR/WAR file from the 'builder' stage into this new stage
# Assumes your build produces a single JAR in the default location
COPY --from=builder /app/build/libs/*.jar app.jar

# Define default environment variables for Spring Boot profiles (example)
ENV SPRING_PROFILES_ACTIVE=production

# Expose the port your Spring Boot app runs on (default is 8080)
EXPOSE 8080

# Define the command that runs when the container starts
# The exec form of ENTRYPOINT is preferred
ENTRYPOINT ["java", "-jar", "app.jar"]

# Optionally, you can use CMD to provide default arguments to the ENTRYPOINT
# CMD ["--spring.profiles.active=production"]