🏛️ Reveila Sovereign Node: Production Operations Guide
This directory contains the operational logic for the Reveila Agentic AI Runtime Fabric.

1. Directory Structure Overview
/bin: Centralized lifecycle scripts (Startup, Shutdown, Orchestration).

/libs: Stable application binaries (Fat JARs).

/data: The Sovereign Perimeter (Persistent PostgreSQL and Reasoning Traces).

/infrastructure: Container orchestration logic (Dockerfile, Docker-Compose).

2. Core Command Suite
Command	Platform	Description
startup.bat	Windows	Bootstraps infra and launches the Fabric natively or via Docker.
startup.sh	Linux/Mac	POSIX-compliant launcher for Edge and Container environments.
stop.bat/sh	Cross-platform	Gracefully shuts down the node and reclaims system resources.
reveila-up.ps1	Windows	Dedicated infrastructure orchestrator (DB & Network check).

3. Operational Best Practices
Graceful Shutdown: Always use stop.bat or stop.sh before system reboots to ensure the Flight Recorder flushes all reasoning traces to the persistent database.

Hot-Swapping Logic: To update the AI logic without a full rebuild, replace the reveila-suite-fat.jar in the /libs folder and execute a restart.

Data Sovereignty: All audit logs and forensic data are stored in /data/postgres. This folder should be included in your standard enterprise backup rotation.

Environment Overrides: You can modify JVM memory constraints or Spring profiles by setting JAVA_MEMORY_OPTS or SPRING_PROFILES_ACTIVE in your host environment variables.