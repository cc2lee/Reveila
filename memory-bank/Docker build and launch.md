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

## Step 5: Covert Windows new line character
Optional step if the scripts in /bin are using \r\n as new line character

```bash
PowerShell: Get-ChildItem *.sh | ForEach-Object { $content = Get-Content $_.FullName; [IO.File]::WriteAllLines($_.FullName, $content) }
Bash: sed -i 's/\r$//' *.sh
```

## Step 6: Monitor the Logs
Verify that the Spring Boot application has successfully connected to PostgreSQL and initialized the system.

```bash
# Tail the application logs
docker logs -f reveila-fabric-prod
```

## Convenience Single Command line to start Docker

```bash
docker-compose -f docker-compose.prod.yml up --build -d; docker logs -f reveila-fabric-prod
```

## Shut down Docker
In "infrastructure" directory, use these commands:

The "Pause" (Standard):
```bash
docker-compose stop
```
Stops the containers but keeps them in the system. Fast to restart.

The "Clean Slate" - wipe the container (Recommended for End of Day):
```bash
docker-compose down
```
Stops the containers and removes them from Docker’s active process list. Because your data is in a volume (../data), nothing is lost.


## The "Prune"
Every few weeks, Docker can accumulate "dangling" images (failed builds or old layers). To keep your machine lean, run:
```bash
docker system prune
```
(This won't touch your volumes/data, just the temporary "junk" files.)


## Rebuild and Launch
```bash
docker-compose up --build -d
```

## The "Single Source of Truth" Rule
Use start.bat if you want to launch the Reveila Fabric as a native Windows process (bare metal) while automatically bootstrapping the Postgres container in the background.

Use docker-compose if you want to run the entire stack (App + DB) inside the Docker sandbox.





The "Single Source of Truth" rule is about defining exactly which environment owns the execution and which environment owns the state at any given moment.In your architecture, you have two distinct "Modes of Sovereignty." Understanding the difference ensures you don't accidentally create a "Split-Brain" scenario where two different versions of your database or application are fighting for control.1. The "Hybrid" Mode (start.bat)The Scenario: You want to develop or demo the Fabric with maximum performance and visibility on your local machine, but you don't want to deal with the headache of installing and managing a local PostgreSQL server.Application (Bare Metal): The Java/Spring Boot app runs directly on your Windows OS. This is ideal for debugging because you have direct access to the console, your IDE's debugger, and local file paths.Infrastructure (Docker): The script calls docker-compose only for the PostgreSQL service. Docker acts as a "Virtual Appliance" that provides the database without cluttering your Windows Registry or services.Why use this? It's the fastest way to see code changes. You rebuild the JAR, hit start.bat, and the app is live in seconds, talking to a stable, containerized DB.2. The "Sovereign Node" Mode (docker-compose)The Scenario: You want to demonstrate the Reveila Suite as it would look in a real-world production environment or an "Edge Server" at a client's site.Entire Stack (Docker Sandbox): Both the Spring Boot Fabric and the PostgreSQL Database are "sealed" inside the Docker network.Isolation: The application has no "knowledge" of your Windows OS. It only sees the virtual network provided by Docker. This is the ultimate security moat—if an agent were compromised, it is trapped inside the container.Why use this? This is your "Pitch Mode." It proves the platform is portable, secure, and ready for enterprise-grade deployment. You are running the exact same image that would run on an AWS Graviton or a hardened Linux server.

📊 Decision Matrix: Which one to run?RequirementUse start.bat (Hybrid)Use docker-compose (Sandbox)Development Speed⭐⭐⭐ (Fastest iteration)⭐ (Requires image rebuild)Security Isolation⭐ (Shared with Host)⭐⭐⭐ (Hardened Sandbox)Performance⭐⭐⭐ (Native JRE)⭐⭐ (Slight Docker overhead)"Pitch" Realism⭐⭐⭐⭐⭐ (Enterprise ready)Debugging⭐⭐⭐ (IDE friendly)⭐ (Logs only)

The Protocol:

To switch from Hybrid to Sandbox: Run stop.bat first to kill the native Java process and the background DB. Then run your docker-compose command.

To switch back: Run docker-compose down. Then run start.bat.