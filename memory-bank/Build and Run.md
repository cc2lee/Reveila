# Build and Run Instructions

This guide provides the necessary steps to build the Reveila Suite and run the AI Runtime Fabric either locally or via Docker.

## Local JVM Deployment (Java 21+)

### 1. Build the Artifacts
Build the executable fat JAR using the Gradle wrapper:
```powershell
.\gradlew.bat clean bootJar
```

### 2. Launch Locally
Start the application using the provided startup script:
```powershell
.\startup.bat
```

---

## Docker Deployment (Production)

For containerized environments, use the following steps to build and orchestrate the full stack.

### 1. Build the Production Image
Navigate to the infrastructure directory and build the Docker image:
```powershell
cd infrastructure
docker build -t reveila-fabric:1.0.0 .
```

### 2. Orchestrate Live Environment with Docker Compose
Launch the AI Fabric along with the Sovereign Data Node (PostgreSQL):
```powershell
docker-compose -f docker-compose.prod.yml up -d
```

Alternative commands for convenience:
*   **Default (if .env is set):** `docker-compose up -d`
*   **Restart specific service:** `docker-compose -f docker-compose.prod.yml restart reveila-fabric`

### 3. Hot-Reload Build: Build + Orchestrate
Use this "Super Command" to detect changes in the JAR or Dockerfile and rebuild the Fabric image automatically without touching the Database:
```powershell
docker-compose -f docker-compose.prod.yml up -d --build
```

### 4. Verify Deployment
Check the status of the running containers:
```powershell
docker ps
```

### 5. Monitor Initialization
Follow the application logs to ensure successful startup:
```powershell
docker logs -f reveila-fabric-container
```
Verify the following indicators in the log stream:
*   `Started Application in ... seconds`
*   `REVEILA_HOME set to /reveila`

---

## Connectivity & Health

### Web Dashboard
Access the control center via your browser:
*   [http://localhost:8080/](http://localhost:8080/)

### API Health Check
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

Go to Vue project folder: npm run build

Copy the contents of the dist folder into: src/main/resources/static/

Rebuild the JAR: mvn clean package

Rebuild the Docker Image: docker-compose up -d --build