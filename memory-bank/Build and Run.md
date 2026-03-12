# 🏗️ Build and Run Instructions

This guide provides the necessary steps to build the **Reveila Suite** and run it either locally or via Docker.

---

## 📋 Requirements
- **Java:** Version 21 or higher.
- **Docker:** Installed and running (for database or containerized deployment).
- **Node.js:** Required for frontend builds (managed automatically by Gradle).

---

## 💻 Development (Local JVM)

This mode is ideal for active development, allowing for fast iterations and direct debugging.

### 1. Build the Artifacts
Build the executable fat JAR using the Gradle wrapper:
```powershell
.\gradlew.bat clean bootJar
```

### 2. Start the Docker PostgreSQL Database
Ensure Docker is running, then start the PostgreSQL container in the background:
```powershell
cd system-home/standard/infrastructure
docker-compose -f docker-compose.prod.yml up -d reveila-db-prod
```
> [!NOTE]
> Since the `docker-compose.prod.yml` maps port `5432:5432`, the database is accessible at `localhost:5432` from your Windows host.

### 3. Configure `system.home`
The Reveila engine requires the `system.home` property to locate configurations, plugins, and data. You can set it in two ways:

1. **Environment Variable (Recommended):** Set `REVEILA_HOME` on your host machine to the absolute path of `system-home/standard`.
2. **Application Argument:** Pass it to the Gradle task using `--args`.

#### VS Code `launch.json` Example:
```json
{
    "type": "java",
    "name": "Reveila-on-Spring",
    "request": "launch",
    "mainClass": "com.reveila.spring.system.Application",
    "projectName": "spring:core",
    "vmArgs": "-Xmx2048m -Dsystem.home=../../system-home/standard -Dspring.profiles.active=dev"
}
```

### 4. Start the Reveila Server
Launch the server using Gradle. If you haven't set `REVEILA_HOME`, you must pass the path as an argument:
```powershell
# Using environment variable (REVEILA_HOME)
.\gradlew.bat :spring:core:bootRun

# Using application arguments
.\gradlew.bat :spring:core:bootRun --args="system.home=../../system-home/standard"
```

💡 **Tip:** To see standard logs without Gradle's progress bar (useful for debugging hangs):
```powershell
.\gradlew.bat :spring:core:bootRun --console=plain
```

### 5. Debugging
In VS Code, select the **Reveila-on-Spring** configuration and press **F5**. The application will connect to the PostgreSQL instance running inside Docker.

---

## 🚀 Deployment (Bare Metal)

### 1. Build the Artifacts
```powershell
.\gradlew.bat clean bootJar
```

### 2. Prepare the System Home
The build process places the fat JAR in `system-home/standard/libs`. You can copy the entire `system-home/standard` directory to your target server.

### 3. Launch the Node
Use the provided startup scripts in the `bin` directory:
```powershell
# Windows
cd system-home/standard/bin
.\startup.bat

# Linux
cd system-home/standard/bin
./startup.sh
```

---

## 🐳 Production (Docker Sandbox)

For production, it is recommended to run the entire stack within Docker for maximum isolation.

### 1. Build the Production Image
```powershell
cd system-home/standard/infrastructure
docker build -t reveila-fabric:1.0.0 .
```

### 2. Orchestrate the Stack
```powershell
docker-compose -f docker-compose.prod.yml up -d
```

### 3. Monitor and Verify
```powershell
docker ps
docker logs -f reveila-fabric-container
```

💡 **One-liner:** `docker-compose -f docker-compose.prod.yml up --build -d; docker logs -f reveila-fabric-container`

---

## 🛠️ Maintenance & Operations

### Switching Environments
- **Local → Docker:** Stop the Gradle process, run `docker-compose down`, then `docker-compose up -d`.
- **Docker → Local:** Run `docker-compose down`, start only the DB with `docker-compose up -d reveila-db-prod`, then launch via Gradle.

### Docker Cleanup
| Command | Action |
| :--- | :--- |
| `docker-compose stop` | Pauses containers (fast restart). |
| `docker-compose down` | Stops and removes containers (clean slate). |
| `docker system prune` | Removes unused data/images (maintenance). |

---

## 🔍 Troubleshooting

### 🔑 Database Authentication Failure
If you see "password authentication failed," your Docker volume might have old credentials.

**To reset:**
1. Stop containers: `docker-compose down`
2. Delete persisted data: Remove `system-home/standard/data/postgres`
3. Restart: `docker-compose up -d reveila-db-prod`

### 🌐 Docker Networking Handshake
Verify connectivity between the Fabric and the Database:

1. **Inspect Network:** `docker network inspect infrastructure_default`
2. **Ping Database:** `docker exec -it reveila-fabric-container ping reveila-db-prod`
3. **Check Connection Logs:** `docker logs reveila-fabric-container | findstr "HikariPool"`

---

## 🔗 Useful Links
- **Dashboard:** [http://localhost:8080/](http://localhost:8080/)
- **Health Check:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
