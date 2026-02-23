#!/bin/bash

# 1. Define Environment Variables
APP_JAR="bin/reveila-suite-fat.jar"
JAVA_OPTS="-Xms512m -Xmx2g -Dspring.profiles.active=demo -Dfile.encoding=UTF-8"

echo "--- Reveila Agentic AI Runtime Fabric ---"

# 2. Bootstrap Sovereign Infrastructure
if [ -f "./reveila-up.sh" ]; then
    echo "[System] Orchestrating Infrastructure..."
    chmod +x ./reveila-up.sh
    ./reveila-up.sh
else
    echo "[Warning] reveila-up.sh not found. Proceeding with existing environment..."
fi

# 3. Validation Check
if [ ! -f "$APP_JAR" ]; then
    echo "[Error] Fat JAR not found at $APP_JAR. Please run './gradlew build' first."
    exit 1
fi

# 4. Launch the Fabric
echo "[Reveila] Starting Sovereign Node..."
java $JAVA_OPTS -jar $APP_JAR