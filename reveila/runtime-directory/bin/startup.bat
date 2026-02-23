@echo off
SETLOCAL

REM Get the directory where the script is located (the /bin folder)
SET BIN_DIR=%~dp0
REM Set REVEILA_HOME as one level up from /bin
SET REVEILA_HOME=%BIN_DIR%..
REM Point to the stable distribution in /libs
SET APP_JAR=%REVEILA_HOME%\libs\reveila-suite-fat.jar

REM 1. Bootstrap Sovereign Infrastructure
if exist "%BIN_DIR%reveila-up.ps1" (
    powershell -ExecutionPolicy Bypass -File "%BIN_DIR%reveila-up.ps1"
)

REM 2. Set Architectural Constraints
SET JAVA_OPTS=-Xms512m -Xmx2g -Dspring.profiles.active=demo -Dfile.encoding=UTF-8

REM 3. Launch the Sovereign Fabric
echo [Reveila] Starting Agentic AI Runtime Fabric...
if exist "%APP_JAR%" (
    java %JAVA_OPTS% -jar "%APP_JAR%"
) else (
    echo [Error] Fat JAR not found at %APP_JAR%
    pause
)

ENDLOCAL