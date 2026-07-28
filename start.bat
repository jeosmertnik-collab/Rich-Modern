@echo off
title Excel Client Loader
color 0B
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

echo.
echo  ================================
echo     EXCEL CLIENT - Loader
echo  ================================
echo.

set "ROOT=%~dp0"
cd /d "%ROOT%"

REM ===== JAVA 21 =====
echo [1/3] Java 21...

set "JAVA_DIR=%ROOT%jre"
set "JAVA_EXE=%JAVA_DIR%\bin\java.exe"

if exist "%JAVA_EXE%" (
    echo   Found in jre folder.
    goto :java_ok
)

if exist "%TEMP%\jdk21\jdk-21.0.2\bin\java.exe" (
    set "JAVA_EXE=%TEMP%\jdk21\jdk-21.0.2\bin\java.exe"
    echo   Found in temp.
    goto :java_ok
)

REM Check system java >= 21
java -version 2>&1 | findstr /C:"21" >nul 2>&1
if %errorlevel%==0 (
    echo   Found system Java 21.
    set "JAVA_EXE=java"
    goto :java_ok
)

echo   Downloading OpenJDK 21...
curl.exe -L -o "%TEMP%\jdk21.zip" "https://download.java.net/java/GA/jdk21.0.2/f2283984656d49d69e91c558476027ac/13/GPL/openjdk-21.0.2_windows-x64_bin.zip" --progress-bar
if %errorlevel% neq 0 (
    echo   [ERROR] Download failed.
    pause
    exit /b 1
)

powershell -Command "Expand-Archive -Path '%TEMP%\jdk21.zip' -DestinationPath '%TEMP%\jdk21' -Force"
del "%TEMP%\jdk21.zip" >nul 2>&1

if exist "%TEMP%\jdk21\jdk-21.0.2\bin\java.exe" (
    set "JAVA_EXE=%TEMP%\jdk21\jdk-21.0.2\bin\java.exe"
    echo   Installed.
) else (
    echo   [ERROR] Install failed.
    pause
    exit /b 1
)

:java_ok
echo.

REM ===== BUILD =====
echo [2/3] Building...

set "JAVA_HOME="
if not "%JAVA_EXE%"=="java" (
    for %%F in ("%JAVA_EXE%") do set "JAVA_HOME=%%~dpF.."
)

call gradlew.bat build --no-daemon -q 2>&1
if %errorlevel% neq 0 (
    echo   [ERROR] Build failed.
    pause
    exit /b 1
)
echo   Done.

REM ===== RUN =====
echo [3/3] Launching game...
echo.

start "Excel Client" /min cmd /c "cd /d "%ROOT%" && call gradlew.bat runClient --no-daemon"
exit
