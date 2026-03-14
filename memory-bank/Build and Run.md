# 🏗️ Build and Run Instructions

This guide provides the necessary steps to build the **Reveila Suite** and run it either locally or via Docker.

---

## ☀️ Your Morning "One-Click" Cheat Sheet

### Option 1: Hybrid Development (Local App + Docker Infrastructure)
Use this if you are running the Spring Boot application from VS Code or Gradle (`bootRun`).
1. **The Launch (Infrastructure Only):**
   ```powershell
   cd system-home/standard/infrastructure
   docker compose -f docker-compose.prod.yml -f sandbox/docker-compose.sandbox.yml up -d reveila-db-prod ollama-service
   ```
2. **The App Start:** Run `.\gradlew.bat :spring:core:bootRun` or press **F5** in VS Code.
3. **The AI Check:**
   ```powershell
   docker exec -it infrastructure-ollama-service-1 ollama list
   ```

### Option 2: Full Stack (Everything in Docker)
Use this for a production-like demonstration or testing the portable image.
1. **The Launch (Everything):**
   ```powershell
   cd system-home/standard/infrastructure
   docker compose -f docker-compose.prod.yml -f sandbox/docker-compose.sandbox.yml up -d
   ```
2. **The Log Check:** `docker logs -f reveila-fabric-container`
3. **The AI Check:** `docker exec -it infrastructure-ollama-service-1 ollama list`

---

## 📋 Requirements
- **Java:** Version 21 or higher.
- **Docker:** Installed and running (for database or containerized deployment).
- **Node.js:** Required for frontend builds (managed automatically by Gradle).

---

## 📂 Deployment Directory Structure

The system is designed to run within a structured home directory:

```text
${REVEILA_HOME}/
├── bin/            # Startup and control scripts
├── configs/        # Environment-specific properties and component JSONs
├── data/           # Persistent storage (Postgres data, JSON fallbacks)
├── infrastructure/ # Docker Compose and Dockerfiles
├── libs/           # Compiled Java artifacts (Fat JAR)
├── logs/           # System and audit logs
├── plugins/        # Dynamic AI agent plugins
├── resources/      # Static assets and DB schemas
└── web/            # Built Vue.js dashboard files
```

---

## 💻 Development (Local JVM)

### 1. Choose the Mode for Development

#### Running Locally via Gradle (`bootRun`)
**No need** to manually build the JAR.
- When you run `.\gradlew.bat :spring:core:bootRun`, Gradle automatically detects changed source files, recompiles them, and launches the application with the latest classes.
- **Tip:** Since [`spring-boot-devtools`](spring/core/build.gradle.kts:61) is already in your project, if you are running in an IDE (like VS Code or IntelliJ), the app will often "hot-reload" automatically when you save a file.

#### Running via Docker (`docker compose`)
**Must** rebuild the JAR.
- The Docker container is configured to run the fat JAR located at [`system-home/standard/libs/reveila-suite-fat.jar`](system-home/standard/infrastructure/docker-compose.prod.yml:53).
- **Workflow:**
  1. Make code changes.
  2. Run `.\gradlew.bat bootJar` (to update the file in `libs`).
  3. Restart your Docker container: `docker compose -f docker-compose.prod.yml restart reveila-fabric`.

### 2. Start the Sovereign Data Node (Postgres + pgvector)
The system uses `ankane/pgvector` for native AI embedding support.
```powershell
cd system-home/standard/infrastructure
docker compose -f docker-compose.prod.yml up -d reveila-db-prod
```
> [!NOTE]
> The database is accessible at `localhost:5432` from your host machine.

### 3. Configure `system.home`
The Reveila engine requires the `system.home` property to locate its resources.
1. **Environment Variable (Recommended):** Set `REVEILA_HOME` to the absolute path of `system-home/standard`.
2. **Application Argument:** Pass it directly to Gradle using `--args`.

### 4. Start the Reveila Server
```powershell
# Using REVEILA_HOME env variable
.\gradlew.bat :spring:core:bootRun

# Using explicit path argument
.\gradlew.bat :spring:core:bootRun --args="system.home=../../system-home/standard"
```

💡 **Tip:** Use `--console=plain` to see raw logs without the Gradle progress bar.

---

## 🐳 Production Deployment (Docker)

For production, you have two options depending on how you want to manage your files.

### Option A: Portable Standalone Image (Recommended for Demos)
This method bakes your JAR, configs, and web files directly into the Docker image. You can deploy **just the image** to a new machine without copying the project folders.

#### 1. Build the Standalone Image
```powershell
cd system-home/standard/infrastructure
docker compose -f docker-compose.standalone.yml build
```

#### 2. Launch the Stack
```powershell
docker compose -f docker-compose.standalone.yml up -d
```

---

### Option B: Bind Mounts (Recommended for Local Testing)
This method "borrows" the files from your host machine. It is great for testing small changes without rebuilding the image. **Requires copying the `system-home/standard` folder to the target machine.**

#### 1. Build the Generic Image
```powershell
cd system-home/standard/infrastructure
docker build -t reveila-fabric:1.0.0 .
```

#### 2. Launch the Stack
```powershell
docker compose -f docker-compose.prod.yml up -d
```

---

## 🛠️ Common Deployment Steps (All Docker Modes)

### 1. Build the Clean Fat Jar
Regardless of the mode, ensure you have a fresh JAR:
```powershell
.\gradlew.bat clean bootJar
```

### 2. Orchestrate the Secrets
Create a `.env` file in `system-home/standard/infrastructure` to securely store your credentials:
```text
DB_USER=admin
DB_PASSWORD=your_secure_password
DB_NAME=reveila_db
```

### 3. Verify and Monitor
```powershell
docker ps
docker logs -f reveila-fabric-container
```

---

## 🎮 The Experimental Sandbox ("Playground")

To run the full suite including local AI models (**Ollama**), use the merge mode:
```powershell
docker compose -f docker-compose.prod.yml -f sandbox/docker-compose.sandbox.yml up -d --build
```

### AI Handshake Verification
```powershell
# Check available models
curl http://localhost:11434/api/tags

# Manually pull Llama 3 if missing
curl -X POST http://localhost:11434/api/pull -d '{"name": "llama3"}'
```

---

## 🛠️ Maintenance & Troubleshooting

### 🔑 Resetting Credentials
If you encounter "password authentication failed," your Docker volume may contain stale data.
1. `docker compose -f docker-compose.prod.yml down`
2. Delete `system-home/standard/data/postgres` folder.
3. `docker compose -f docker-compose.prod.yml up -d reveila-db-prod`

### 🌐 Networking Check
Verify internal communication between containers:
```powershell
docker exec -it reveila-fabric-container ping reveila-db-prod
```

### 🔗 Useful Links
- **Dashboard:** [http://localhost:8080/](http://localhost:8080/)
- **Health Check:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)


## Development Tips

1. Force a Full Rebuild
The most reliable way to ensure the changes in the reveila:core module are linked to your spring:core application is to run a full build from the terminal:

.\gradlew.bat clean classes

Wait for this to finish successfully. It will recompile the new resolver logic in the core module.

2. Refresh the Java Language Server (If Step 1 fails)
If you still see the error, VS Code's internal Java cache might be stuck:

Open the Command Palette (Ctrl+Shift+P).
Type: Java: Clean Java Language Server Workspace.
Click Restart and delete.

3. Check your launch.json
If you are using a custom launch configuration and do not have the system environment variable REVEILA_HOME set, ensure you are passing the system.home property correctly, as the Spring app needs it to find the reveila.properties file:

"vmArgs": "-Dsystem.home=../../system-home/standard"

4. Monitor Logs
From the "infrastructure" directory, run:
docker compose -f docker-compose.prod.yml -f sandbox/docker-compose.sandbox.yml logs -f reveila-db-prod ollama-service


5. bootRun vs. bootJar

### 🚀 `.\gradlew.bat clean bootRun`
*   **What it does:** Compiles your code and **immediately launches** the application.
*   **Result:** You get a live, running server in your terminal.
*   **Primary Use:** **Daily Development**. Use this when you want to quickly test your changes locally on your machine.

### 📦 `.\gradlew.bat clean bootJar`
*   **What it does:** Compiles your code and packages it into a **standalone executable file** (the "Fat JAR").
*   **Result:** A file is created at [`system-home/standard/libs/reveila-suite-fat.jar`](memory-bank/Build%20and%20Run.md:61).
*   **Primary Use:** **Deployment**. Use this when you are ready to update your **Docker** containers or move the application to a different server.

---

### Summary Table
| Feature | `bootRun` | `bootJar` |
| :--- | :--- | :--- |
| **Action** | Run the app | Build the file |
| **Output** | A running process | A `.jar` file |
| **Docker** | No impact | **Required** for Docker updates |
| **Speed** | Faster iteration | Slower (packaging overhead) |

**Recommendation:** For your current task of verifying the new Java logic, use **`bootRun`**.