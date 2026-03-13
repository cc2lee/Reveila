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


## Miseneous
---
To avoid broken paths, run Docker commands at the root of `system-home`.

Docker merges compose files if more than one is specified, following the rule:
Simple Values (image, container_name): The last file wins. It replaces the value.
Mappings (environment, labels): They are merged. If both files have DEBUG, the value in the second file wins. If only one has API_KEY, it stays.
Lists (ports, volumes): These are concatenated (appended). This is usually where the trouble starts if both define port 5432:5432 - Docker will try to bind it twice and fail.

Instead of one giant file, we use docker-compose.prod.yml as the "base" and use additional compose files, e.g. the docker-compose.sandbox.yml, to only add what's missing.

To see what the final, merged file looks like before you run it, use the config command:

Bash

docker compose \
  -f standard/infrastructure/docker-compose.prod.yml \
  -f sandbox/infrastructure/docker-compose.sandbox.yml \
  --project-directory . \
  config


If that looks good, spin it up with:

Bash

docker compose \
  -f standard/infrastructure/docker-compose.prod.yml \
  -f sandbox/infrastructure/docker-compose.sandbox.yml \
  --project-directory . \
  up -d

One "Gotcha" to watch for:
If your docker-compose.prod.yml has ports: - "5432:5432" for Postgres, do not include a ports section for Postgres in the sandbox file. If you do, Docker will try to open the port twice, and you'll get a "Bind for 0.0.0.0:5432 failed" error.

### Switch .env file
docker compose --env-file .env.prod up -d

To Run the Sandbox (Merge Mode):

Run from "infrastructure" directory:

docker compose -f docker-compose.prod.yml -f sandbox/docker-compose.sandbox.yml up -d --build

What happens: In addition to services from prod.yml, Docker loads additional services defined in sandbox.yml including the ollama-service and model-puller.


To Run Production Only:

Bash

docker compose -f docker-compose.prod.yml up -d

What happens: Only the core app and the DB start. Clean and lean.


standard/
├── infrastructure/
│   ├── docker-compose.prod.yml
│   └── sandbox/
│       ├── docker-compose.sandbox.yml
│       └── .env.sandbox
├── bin/
├── configs/
└── (other app folders)


Run this command in PowerShell to see exactly what is hogging the port:
netstat -ano | findstr :8080




"Handshake Check" to ensure the Reveila Fabric can actually talk to the Ollama model engine through the internal Docker network.

1. Verify the AI Model Status
First, let's see if Llama 3 is actually inside the engine. Run this in your PowerShell:

PowerShell

docker exec -it infrastructure-ollama-service-1 ollama list
If you see llama3: We are in business.

If the list is empty: The model-puller might still be working or needs a kickstart.

2. The "Fabric-to-Model" Ping
This is the most important test. It confirms that your Spring Boot app (the Fabric) can reach the AI "brain." We'll use curl from within the Fabric container to simulate the internal connection:

PowerShell

curl http://localhost:11434/api/tags
Why this matters: If this returns a JSON list of models, it proves your Docker networking is perfect. The Fabric "referee" now has a clear line of sight to the LLM.

Why the models are missing?
If you see {"models": []}, it means the Ollama service started but never received the command to download Llama 3. This usually happens if the model-puller container ran into an error or started before Ollama was ready to receive API calls.

Let's force the pull manually from your host (since port 11434 is open):
Run this in PowerShell to start the download right now:

PowerShell

curl -X POST http://localhost:11434/api/pull -d '{"name": "llama3"}'
Wait for this to finish (you'll see a progress bar if using a modern terminal). Once done, run your api/tags command again, and you should see Llama 3 listed.


3. Check the Sovereign Ledger (Postgres)
Let's make sure the database is ready to record those agentic interactions. Run this to verify the table we created earlier is visible to the container:

PowerShell

docker exec -it reveila-db-prod psql -U admin -d reveila_db -c "\dt governance_audit"


It looks like you’re ready to run ollama list to see what’s inside. Since your container is named infrastructure-ollama-service-1, run this command to see the status of your models:

PowerShell

docker exec -it infrastructure-ollama-service-1 ollama list
What to look for:
If you see llama3 and phi3: Your pre-pulling strategy worked perfectly. You are ready to start the "Negotiation" demo.

If the list is empty: The model-puller container might have hit a network snag or is still downloading. (Llama 3 is about 4.7 GB, so it can take a few minutes depending on your Montgomery ISP speed).

How to check the download progress:
If the list was empty, run this to see the "live" status of the puller:

PowerShell

docker logs -f model_puller
docker logs model_puller