@echo off
SETLOCAL ENABLEDELAYEDEXPANSION

SET BIN_DIR=%~dp0
SET REVEILA_HOME=%BIN_DIR%..
SET APP_JAR=%REVEILA_HOME%\libs\reveila-suite-fat.jar
SET INFRA_DIR=%REVEILA_HOME%\infrastructure

echo [Audit] Pre-flight Environment Check...

REM --- Java 21 Resolution ---
set "JAVA_EXE=java"

if defined JAVA_HOME (
    if exist "!JAVA_HOME!\bin\java.exe" (
        set "JAVA_EXE=!JAVA_HOME!\bin\java.exe"
        echo [Status] Using JAVA_HOME: !JAVA_HOME!
    )
)

REM Verify if resolved Java is version 21
for /f "tokens=3" %%g in ('"!JAVA_EXE!" -version 2^>^&1 ^| findstr /i "version"') do (
    set "JVER=%%g"
    set "JVER=!JVER:"=!"
    for /f "delims=. tokens=1" %%v in ("!JVER!") do set "MAJOR_VER=%%v"
)

if !MAJOR_VER! LSS 21 (
    echo [Error] Reveila requires Java 21. Found Version: !MAJOR_VER!
    echo [Action] Please set JAVA_HOME to C:\IDE\JDK\jdk-21.0.9+10
    pause
    exit /b 1
)

REM --- Infrastructure Hand-off (Omitted for brevity, keep your existing code here) ---
REM ... (Port checks and reveila-up.ps1 call) ...

echo [Reveila] Starting Agentic AI Runtime Fabric...
if exist "%APP_JAR%" (
    "!JAVA_EXE!" -Xms512m -Xmx2g -jar "%APP_JAR%"
)
ENDLOCAL