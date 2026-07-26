@echo off
chcp 65001 >nul
title Rich Modern Launcher

echo ========================================
echo         RICH MODERN LAUNCHER
echo ========================================
echo.

:: Find .minecraft
set MC_DIR=%APPDATA%\.minecraft
if exist "%MC_DIR%" goto :found

set MC_DIR=%USERPROFILE%\.minecraft
if exist "%MC_DIR%" goto :found

echo [!] .minecraft not found in %APPDATA%
echo [!] Enter your .minecraft folder path:
set /p MC_DIR=^> 
if not exist "%MC_DIR%" (
    echo [!] Folder not found. Exiting.
    pause
    exit /b 1
)

:found
echo [OK] Minecraft: %MC_DIR%

:: Find Fabric
set FABRIC_DIR=
set FABRIC_JAR=
for /d %%D in ("%MC_DIR%\versions\*fabric*") do (
    set FABRIC_DIR=%%D
    set FABRIC_VER=%%~nxD
)
if not defined FABRIC_DIR (
    echo [!] Fabric not found!
    echo [!] Install Fabric 1.21.11 from https://fabricmc.net
    echo     Run Fabric Installer, select Minecraft 1.21.11, click Install.
    echo     Then launch Minecraft with Fabric profile once.
    pause
    exit /b 1
)
echo [OK] Fabric: %FABRIC_VER%

:: Download JAR if not present
set JAR_NAME=rich-1.0.01.jar
set MODS_DIR=%MC_DIR%\mods
if not exist "%MODS_DIR%" mkdir "%MODS_DIR%"
set TARGET=%MODS_DIR%\%JAR_NAME%

if not exist "%TARGET%" (
    echo [..] Downloading %JAR_NAME%...
    curl -L -o "%TARGET%" "https://github.com/jeosmertnik-collab/Rich-Modern/releases/download/v1.0.01/%JAR_NAME%" 2>nul
    if not exist "%TARGET%" (
        echo [!] Download failed. Download manually:
        echo     https://github.com/jeosmertnik-collab/Rich-Modern/releases
        echo     Put %JAR_NAME% into: %MODS_DIR%
        pause
        exit /b 1
    )
)
echo [OK] Mod: %JAR_NAME%

:: Find Java
set JAVA_EXE=javaw
if exist "%MC_DIR%\..\java-runtime-gamma\bin\javaw.exe" set JAVA_EXE=%MC_DIR%\..\java-runtime-gamma\bin\javaw.exe
if exist "%MC_DIR%\runtime\bin\javaw.exe" set JAVA_EXE=%MC_DIR%\runtime\bin\javaw.exe
where javaw >nul 2>nul || set JAVA_EXE=java
echo [OK] Java: %JAVA_EXE%

:: Read Fabric version JSON
set JSON=%FABRIC_DIR%\%FABRIC_VER%.json
if not exist "%JSON%" (
    echo [!] %FABRIC_VER%.json not found
    pause
    exit /b 1
)

:: Build classpath from JSON (find libraries entries)
set CP=

:: Use PowerShell to parse the JSON and build classpath
for /f "delims=" %%L in ('powershell -NoProfile -Command ^
    "$j = Get-Content '%JSON%' -Raw | ConvertFrom-Json; ^
     $libs = $j.libraries | Where-Object { $_.name -notmatch '^net\.fabricmc:' -or $_.name -match 'fabricloader' } | ^
     ForEach-Object { $parts = $_.name -split ':'; ^
     $path = ($parts[0..1] -replace '\.','\\') + '\' + $parts[2] + '\' + $parts[1] + '-' + $parts[2] + '.jar'; ^
     Join-Path '%MC_DIR%\libraries' $path } | ^
     Where-Object { Test-Path $_ }; ^
     $cp = ($libs -join ';') + ';' + '%FABRIC_DIR%\%FABRIC_VER%.jar'; ^
     Write-Output $cp"') do (
    set CP=%%L
)

if not defined CP (
    echo [!] Failed to build classpath. Launching with basic classpath...
    set CP=%FABRIC_DIR%\%FABRIC_VER%.jar
)

:: Get main class
for /f "delims=" %%M in ('powershell -NoProfile -Command ^
    "(Get-Content '%JSON%' -Raw | ConvertFrom-Json).mainClass"') do (
    set MAIN_CLASS=%%M
)
if not defined MAIN_CLASS set MAIN_CLASS=net.fabricmc.loader.impl.launch.knot.KnotClient

echo [OK] MainClass: %MAIN_CLASS%
echo.
echo ========================================
echo         STARTING MINECRAFT...
echo ========================================
echo.

:: Launch
start "" "%JAVA_EXE%" -Xmx2048M -Xms512M ^
    "-Djava.library.path=%FABRIC_DIR%\natives" ^
    -cp "%CP%;%FABRIC_DIR%\%FABRIC_VER%.jar" ^
    %MAIN_CLASS% ^
    --version %FABRIC_VER% ^
    --gameDir "%MC_DIR%" ^
    --assetsDir "%MC_DIR%\assets" ^
    --assetIndex "%FABRIC_VER%"

echo Game launched! You can close this window.
timeout /t 5 >nul
