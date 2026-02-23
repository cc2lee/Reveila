@echo off
SETLOCAL

REM 1. Bootstrap Sovereign Infrastructure
if exist "reveila-up.ps1" (
    powershell -ExecutionPolicy Bypass -File .\reveila-up.ps1
)

REM 2. Set Architectural Constraints
SET JAVA_OPTS=-Xms512m -Xmx2g -Dspring.profiles.active=demo -Dfile.encoding=UTF-8

REM 3. Launch the Sovereign Fabric
echo [Reveila] Starting Agentic AI Runtime Fabric...
java %JAVA_OPTS% -jar bin\reveila-suite-fat.jar

ENDLOCAL