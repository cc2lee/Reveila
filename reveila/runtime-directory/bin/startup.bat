@echo off
SETLOCAL ENABLEDELAYEDEXPANSION

:: 1. Path Resolution
SET "BIN_DIR=%~dp0"
for %%i in ("%BIN_DIR%..") do SET "REVEILA_HOME=%%~fi"
SET "APP_JAR=%REVEILA_HOME%\libs\reveila-suite-fat.jar"
SET "INFRA_DIR=%REVEILA_HOME%\infrastructure"

echo [Status] Root identified as: %REVEILA_HOME%
echo [Audit] Pre-flight Environment Check...

:: 2. Java 21 Resolution
set "JAVA_EXE=java"
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    )
)

:: Create a temporary file to capture the version output
:: This avoids the "Syntax is incorrect" pipe error caused by special characters in paths
"%JAVA_EXE%" -version > "%TEMP%\java_ver.txt" 2>&1
if !errorlevel! neq 0 (
    echo [Error] Could not execute Java at: "%JAVA_EXE%"
    pause
    exit /b 1
)

:: Parse the file for the version number
set "MAJOR_VER=0"
for /f "tokens=3" %%g in ('findstr /i "version" "%TEMP%\java_ver.txt"') do (
    set "JVER=%%g"
    set "JVER=!JVER:"=!"
    for /f "delims=. tokens=1" %%v in ("!JVER!") do set "MAJOR_VER=%%v"
)
del "%TEMP%\java_ver.txt"

if !MAJOR_VER! LSS 21 (
    echo [Error] Reveila requires Java 21. Found Version: !MAJOR_VER!
    echo [Current Path] "%JAVA_EXE%"
    pause
    exit /b 1
) else (
    echo [Pass] Java 21 detected.
)

:: 3. Port Audit (8080)
netstat -ano | findstr :8080 >nul
if !errorlevel! equ 0 (
    echo [Error] Port 8080 is already in use. 
    pause
    exit /b 1
)

:: 4. Infrastructure Hand-off
netstat -ano | findstr :5432 >nul
if !errorlevel! neq 0 (
    echo [System] Database offline. Triggering Sovereign Orchestrator...
        
    set "PS_CMD=powershell"
    where powershell >nul 2>&1
    if !errorlevel! neq 0 (
        if exist "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" (
            set "PS_CMD=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
        )
    )

    "!PS_CMD!" -ExecutionPolicy Bypass -File "%BIN_DIR%reveila-up.ps1"
    if !errorlevel! neq 0 (
        echo [Error] Infrastructure bootstrap failed.
        pause
        exit /b 1
    )
    
    :: EXTENDED WAIT FOR INITIAL VOLUME CREATION
    echo [System] Database is initializing for the first time...
    echo [System] Waiting 30 seconds for Sovereign DB to be fully ready...
    timeout /t 30 /nobreak >nul
) else (
    echo [Status] Port 5432 active. Sovereign DB is healthy.
    :: Brief pause ensures the existing connection is stable
    timeout /t 2 /nobreak >nul
)

:: 5. Final Launch
echo [Reveila] Starting Agentic AI Runtime Fabric...
if exist "%APP_JAR%" (
    :: 1. Move to the actual project root
    cd /d "%REVEILA_HOME%"
    
    :: 2. Set the environment variable explicitly in this session
    set "REVEILA_HOME=%REVEILA_HOME%"
    
    :: 3. Run with the absolute Java path and JVM arguments
    "%JAVA_EXE%" -Xms512m -Xmx2g ^
    -Dsystem.home="%REVEILA_HOME%" ^
    -Dweb-root="%REVEILA_HOME%\web" ^
    -jar "%APP_JAR%"
) else (
    echo [Error] Fat JAR not found at %APP_JAR%
    pause
)

ENDLOCAL