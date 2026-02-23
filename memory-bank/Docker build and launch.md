# Docker Build and Launch Guide

Follow these steps to build and launch your **Reveila Sovereign Node** using Docker.

---

## Step 1: Build the Application Artifact
Before starting the Docker containers, you must generate the latest Fat JAR and ensure it is placed in the runtime's `libs` directory.

```bash
# From the project root
./gradlew clean bootJar (Unix) OR .\gradlew.bat clean bootJar (Windows)

# Ensure the fat JAR is in the runtime libs directory
# Note: The JAR name usually ends with -boot.jar
cp spring/core/build/libs/*-boot.jar ${REVEILA_HOME}/libs/reveila-suite-fat.jar
```

## Step 2: Navigate to the Infrastructure Directory
The Docker orchestration files are located within the runtime's infrastructure folder.

```bash
cd ${REVEILA_HOME}/infrastructure
```

## Step 3: Launch the Containers
The production configuration uses a generic JRE-21 environment and mounts your local runtime directories (`bin`, `libs`, `data`, etc.) into the container.

```bash
docker-compose -f docker-compose.prod.yml up --build -d
```

## Step 4: Verify the Services
Ensure both `reveila-fabric-prod` and `reveila-db-prod` are in the **Up** state.

```bash
docker ps
```

## Step 5: Monitor the Logs
Verify that the Spring Boot application has successfully connected to PostgreSQL and initialized the system.

```bash
# Tail the application logs
docker logs -f reveila-fabric-prod
```
