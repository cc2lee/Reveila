# Build and Run Instructions

This guide provides the necessary steps to build the Reveila Suite and run the AI Runtime Fabric either locally or via Docker.

## Local JVM Deployment (Java 21+)

### 1. Build the Artifacts
Build the executable fat JAR using the Gradle wrapper:
```powershell
.\gradlew.bat clean bootJar
```

### 2. Start Docker, if not yet running, and then launch the Dockerized PostgreSQL Database
While Docker is running, start the PostgreSQL Docker container in the background:
```powershell
cd system-home/standard/infrastructure
docker-compose -f docker-compose.prod.yml up -d reveila-db-prod
```
*Because the `docker-compose.prod.yml` maps port `5432:5432`, the database will be accessible at `localhost:5432` from your Windows host.*

### 3. Launch the Reveila Server
Start the application using Gradle:
```powershell
.\gradlew.bat :spring:core:bootRun
```
*Note: This will use the `dev` profile by default.*

## Debugging the Hybrid Stack (VS Code + Docker)

You can still use the VS Code debugger for the Spring application while using Docker for the database. This is called **Hybrid Mode**.

### 1. Configure VS Code Launch
Ensure your `.vscode/launch.json` or Spring Boot extension is configured to pass the `system.home` property.

**Recommended VM Arguments for Debugging:**
```text
-Dsystem.home=../../system-home/standard -Dspring.profiles.active=dev
```

### 2. Start Debugging
Select the `Application` class in the `spring/core` module and press **F5**. The application will connect to the PostgreSQL instance running inside Docker as if it were local.

---

## Docker Deployment (Production)

For containerized environments, use the following steps to build and orchestrate the full stack.

### 1. Build the Production Image
Navigate to the system-home infrastructure directory and build the Docker image:
```powershell
cd system-home/standard/infrastructure
docker build -t reveila-fabric:1.0.0 .
```

### 2. Orchestrate Live Environment with Docker Compose
Launch the AI Fabric along with the Sovereign Data Node (Postgres):
```powershell
docker-compose -f docker-compose.prod.yml up -d
```

*Convenience Single Command:*
```powershell
docker-compose -f docker-compose.prod.yml up --build -d; docker logs -f reveila-fabric-container
```

### 3. Hot-Reload Build: Build + Orchestrate
Use this "Super Command" (from the infrastructure directory) to detect changes in the JAR or Dockerfile and rebuild the Fabric image automatically without touching the Database:
```powershell
docker-compose -f docker-compose.prod.yml up -d --build
```

### 4. Shut Down & Maintenance
In the infrastructure directory, use these commands:

**The "Pause" (Standard):**
```powershell
docker-compose stop
```
Stops the containers but keeps them in the system. Fast to restart.

**The "Clean Slate" (Recommended for End of Day):**
```powershell
docker-compose down
```
Stops the containers and removes them from Docker’s active process list. Because your data is in a volume (`../data`), nothing is lost.

**The "Prune" (Monthly Maintenance):**
Every few weeks, Docker can accumulate "dangling" images. To keep your machine lean, run:
```powershell
docker system prune
```

### 5. Verify Deployment
Check the status of the running containers:
```powershell
docker ps
```

### 6. Monitor Initialization
Follow the application logs to ensure successful startup:
```powershell
docker logs -f reveila-fabric-container
```
Verify the following indicators in the log stream:
*   `Started Application in ... seconds`
*   `REVEILA_HOME set to /reveila`

---

## The "Single Source of Truth" Rule

This rule is about defining exactly which environment owns the execution and which environment owns the state at any given moment.

### 1. The "Hybrid" Mode (Local Gradle + Docker DB)
*   **Scenario:** You want to develop or demo the Fabric with maximum performance and visibility on your local machine, but you don't want to deal with the headache of installing and managing a local PostgreSQL server.
*   **Application (Bare Metal):** The Java/Spring Boot app runs directly on your Windows OS via Gradle or IDE. This is ideal for debugging because you have direct access to the console and local file paths.
*   **Infrastructure (Docker):** You run `docker-compose` only for the PostgreSQL service. Docker acts as a "Virtual Appliance" that provides the database without cluttering your system services.

### 2. The "Sovereign Node" Mode (Full Docker Stack)
*   **Scenario:** You want to demonstrate the Reveila Suite as it would look in a real-world production environment.
*   **Entire Stack (Docker Sandbox):** Both the Spring Boot Fabric and the PostgreSQL Database are "sealed" inside the Docker network.
*   **Isolation:** The application has no "knowledge" of your host OS. This is the ultimate security moat—if an agent were compromised, it is trapped inside the container.

📊 **Decision Matrix: Which one to run?**

| Requirement | Local Gradle (Hybrid) | docker-compose (Sandbox) |
| :--- | :--- | :--- |
| **Development Speed** | ⭐⭐⭐ (Fastest iteration) | ⭐ (Requires image rebuild) |
| **Security Isolation** | ⭐ (Shared with Host) | ⭐⭐⭐ (Hardened Sandbox) |
| **Performance** | ⭐⭐⭐ (Native JRE) | ⭐⭐ (Slight Docker overhead) |
| **"Pitch" Realism** | ⭐ | ⭐⭐⭐⭐⭐ (Enterprise ready) |
| **Debugging** | ⭐⭐⭐ (IDE friendly) | ⭐ (Logs only) |

### The Protocol (Switching Environments)
*   **To switch from Hybrid to Sandbox:** Stop the Gradle process and run `docker-compose -f docker-compose.prod.yml down`. Then run the full `docker-compose up` command.
*   **To switch back:** Run `docker-compose down`. Then start the DB with `docker-compose up -d reveila-db-prod` and launch the app via Gradle.

---

## Troubleshooting & Utilities

### 1. PostgreSQL Database Username and Password Mismatch

The PostgreSQL username and password for the Reveila Suite are configured in three primary locations. If you are receiving a "password authentication failed" error, it is likely because the database was previously initialized with different credentials and the state is persisted in a Docker volume.

### Configuration Locations
*   **Application Defaults**: [`spring/core/src/main/resources/application.properties`](spring/core/src/main/resources/application.properties:22)
    ```properties
    spring.datasource.username=${DB_USER:admin}
    spring.datasource.password=${DB_PASSWORD:6788565119}
    ```
*   **External Overrides**: [`system-home/standard/configs/reveila.properties`](system-home/standard/configs/reveila.properties:56)
    ```properties
    DB_USER=admin
    DB_PASSWORD=6788565119
    ```
*   **Docker Infrastructure**: [`system-home/standard/infrastructure/docker-compose.prod.yml`](system-home/standard/infrastructure/docker-compose.prod.yml:46)
    ```yaml
    environment:
      - POSTGRES_USER=admin
      - POSTGRES_PASSWORD=6788565119
    ```

### How to Fix the Authentication Error
If you have already run the database once before these credentials were aligned, the PostgreSQL data volume on your disk still contains the old user/password. Docker **will not** update the password of an existing database even if you change the environment variables.

**To reset the credentials:**
1.  Stop and remove your containers:
    ```powershell
    cd system-home/standard/infrastructure
    docker-compose down
    ```
2.  **Delete the existing database data**:
    Delete the folder `system-home/standard/data/postgres`. This will remove all existing data and force PostgreSQL to re-initialize with the new `admin` credentials on the next start.
3.  Start the database again:
    ```powershell
    docker-compose up -d reveila-db-prod
    ```

### Debugging in VS Code
If you are debugging locally, ensure that your IDE is not overriding these values via environment variables or that you haven't set a different password in a local `.env` file (which takes precedence over `application.properties`).

### 2. Covert Windows New Line Characters
If the scripts in `/bin` are using `\r\n` (Windows) instead of `\n` (Unix), they will fail inside the Linux container. Use these commands to fix them:
*   **PowerShell:** `Get-ChildItem *.sh | ForEach-Object { $content = Get-Content $_.FullName; [IO.File]::WriteAllLines($_.FullName, $content) }`
*   **Bash:** `sed -i 's/\r$//' *.sh`

### 3. Web Dashboard
Access the control center via your browser:
*   [http://localhost:8080/](http://localhost:8080/)

### 4. API Health Check
Verify the node status and system health:
*   [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) (if enabled)

---

## Docker Build vs. Docker-Compose UP

| Feature | `docker build` | `docker-compose up` |
| :--- | :--- | :--- |
| **Primary Goal** | Create a single Image. | Run a multi-service System. |
| **Input File** | `Dockerfile` | `docker-compose.prod.yml` |
| **Result** | An entry in `docker images`. | Running processes in `docker ps`. |
| **Networking** | None (Isolated). | Creates the `infrastructure_default` network. |
| **Volumes** | None (Static). | Maps local data and configs to container. |


🛠️ How to Verify Docker Internal Networking Handshake
After updating any property, run docker-compose up -d. Then use these three commands to verify the Docker internal networking.

1. Inspect the Network Bridge
Verify that both containers are actually standing on the same "patch of grass":

Bash

docker network inspect infrastructure_default
Look for both reveila-fabric-container and reveila-db-prod in the "Containers" list.

2. Test Connectivity from the Fabric
Run a "ping" from inside the Fabric container to see if it can see the DB:

Bash

docker exec -it reveila-fabric-container ping reveila-db-prod
3. Check the Hibernate Logs
Check the logs for the successful connection message:

Bash

docker logs reveila-fabric-container | findstr "HikariPool"
You want to see: HikariPool-1 - Start completed.

🌐 Accessing the Dashboard
If all the above passes, the Agentic AI node is live. Open browser and navigate to:

http://localhost:8080






Ecosystem Build Workflow:

1. **Build Everything**: Running the Gradle `bootJar` task will automatically trigger the Vue build and package it into the final executable JAR.
```powershell
.\gradlew.bat clean bootJar
```

2. **Launch Node**: Use Gradle to launch the Fabric locally.
```powershell
.\gradlew.bat :spring:core:bootRun
```

3. **Rebuild Docker Image** (Optional): If running in Docker Sandbox mode.
```powershell
docker-compose -f docker-compose.prod.yml up -d --build
```