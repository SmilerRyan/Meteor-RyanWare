@echo off
cls
cd /d "%~dp0"

if exist ".\gradle-8.13\bin\gradle.bat" (
    set "GRADLE_HOME=%~dp0\gradle-8.13"
    set "PATH=%GRADLE_HOME%\bin;%PATH%"
)

if exist ".\jdk-21.0.11\bin\java.exe" (
    set "JAVA_HOME=%~dp0\jdk-21.0.11"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)

cmd /c gradlew clean build --no-daemon

if not defined NO_PAUSE pause
