@echo off
setlocal EnableExtensions

cd /d "%~dp0"

echo ========================================
echo Downloading Gradle 8.13 and Java 21 JDK
echo ========================================
echo.

where powershell >nul 2>&1
if errorlevel 1 (
    echo PowerShell is required.
    pause
    exit /b 1
)

echo Downloading Gradle...
set "GRADLE_ZIP=gradle-8.13-bin.zip"
set "GRADLE_URL=https://services.gradle.org/distributions/gradle-8.13-bin.zip"
powershell -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri '%GRADLE_URL%' -OutFile '%GRADLE_ZIP%'"
if not exist "%GRADLE_ZIP%" (
    echo Failed to download Gradle.
    pause
    exit /b 1
)
echo Extracting Gradle...
powershell -Command "Expand-Archive -Path '%GRADLE_ZIP%' -DestinationPath '.' -Force"
echo.

echo Downloading Java JDK...
set "JDK_ZIP=jdk-21_windows-x64_bin.zip"
set "JDK_URL=https://download.oracle.com/java/21/latest/jdk-21_windows-x64_bin.zip"
powershell -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri '%JDK_URL%' -OutFile '%JDK_ZIP%'"
if not exist "%JDK_ZIP%" (
    echo Failed to download Java JDK.
    pause
    exit /b 1
)
echo Extracting Java JDK...
powershell -Command "Expand-Archive -Path '%JDK_ZIP%' -DestinationPath '.' -Force"
echo.

echo Done.
echo.

pause