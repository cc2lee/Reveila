# 🏗️ Build and Run (Server/Desktop)

This guide provides the necessary steps to build the **Reveila Suite** and run it either locally or via Docker.

---

## 📋 Prerequisites
- **Java:** Version 21 (required for Spring Boot backend).
- **Docker Desktop:** Installed and running (for database and local AI models).
- **Node.js:** Required for frontend dashboard (managed automatically by Gradle).
- **PowerShell:** Recommended for running the commands below.

---

## ☀️ The "Morning Launch" (Quick Start)

### Option 1: Hybrid Development (Local JVM + Docker Infrastructure)
*Best for daily coding. The app runs on your machine with fast reloads; the heavy database and AI models run in Docker.*

1. **Launch Infrastructure:**
   ```powershell
   cd system-home/standard/infrastructure
   docker compose -f docker-compose.prod.yml -f sandbox/docker-compose.sandbox.yml up -d reveila-db-prod ollama-service
   ```
2. **Start Backend:** Run `./gradlew.bat :spring:core:bootRun` (or press **F5** in VS Code).
3. **Start Frontend:** 
   ```powershell
   cd web/vue-project
   npm run dev
   ```

### Option 2: Full Stack (Everything in Docker)
*Best for production testing or demos. Everything is isolated in containers.*

1. **Launch Stack:**
   ```powershell
   cd system-home/standard/infrastructure
   docker compose -f docker-compose.standalone.yml up -d --build
   ```
2. **Check Logs:** `docker logs -f reveila-fabric-standalone`
3. **Shutdown:** `docker compose -f docker-compose.standalone.yml down`

---

## 💻 Local Development (Deep Dive)

### 1. The Reveila Home Directory
The system requires a structured home directory to function. This is located at `system-home/standard`. 

**Configuration:** Ensure your IDE or terminal has the `REVEILA_HOME` environment variable set to the absolute path of this folder, or pass it as an argument:
```powershell
./gradlew.bat :spring:core:bootRun --args="system.home=../../system-home/standard"
```

### 2. Frontend Integration
The Spring Boot server hosts the Vue dashboard at `http://localhost:8080`.
- **Production Build:** Gradle handles this automatically during `bootJar`.
- **Development Build:** Run `npm run dev` in `web/vue-project` for Hot Module Replacement (HMR) at `http://localhost:5173`.

### 3. Gradle Command Palette
| Task | Command | When to use |
|---|---|---|
| **Fast Start** | `./gradlew.bat :spring:core:bootRun` | Daily coding & verification. |
| **Full Build** | `./gradlew.bat build -x test` | Checking for compilation issues. |
| **Package** | `./gradlew.bat bootJar` | Preparing for Docker or deployment. |
| **Clean** | `./gradlew.bat clean` | Resolving weird cache or "frozen" code issues. |

---

## 🐳 Docker Deployment Modes

### Portable Standalone (One-Click Demo)
Bakes the JAR, configs, and models into a single portable environment.
```powershell
cd system-home/standard/infrastructure
./gradlew.bat bootJar
docker compose -f docker-compose.standalone.yml build --no-cache
docker compose -f docker-compose.standalone.yml up -d
```

### Bind Mounts (Local Iteration)
Uses the files directly from your `system-home/standard` folder.
```powershell
docker compose -f docker-compose.prod.yml -f sandbox/docker-compose.sandbox.yml up -d
```

---

## 🪲 Troubleshooting & Deep Clean

### 1. Code Changes Not Appearing? ("The Frozen Build")
If you edited `reveila:core` but the Spring app isn't seeing the changes, run:
```powershell
./gradlew.bat clean :reveila:core:classes :spring:core:bootRun
```

### 2. Docker Cache Issues
If you see `failed to solve: parent snapshot does not exist`, your Docker builder cache is corrupted:
```powershell
docker builder prune -a -f
docker system prune -f
```

### 3. Stale Database
If you encounter "password authentication failed" or schema mismatches:
1. `docker compose down`
2. Delete `system-home/standard/data/postgres/`
3. `docker compose up -d reveila-db-prod`

### 4. AI Connection Check
Verify Ollama is reachable and has the required models:
```powershell
# In terminal
curl http://localhost:11434/api/tags
# Manually pull model
docker exec -it ollama-service ollama pull llama3
```

---

## 📱 Mobile Integration (Reveila Mobile)
To connect the mobile app to this desktop suite:
1. Ensure the desktop server is running (Option 1 or 2).
2. Follow the instructions in [`memory-bank/Build and Run Android.md`](memory-bank/Build%20and%20Run%20Android.md).
