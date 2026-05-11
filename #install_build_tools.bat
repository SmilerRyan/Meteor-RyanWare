@echo off
cls
cd /d "%~dp0"

if not exist "gradle-8.13\bin\gradle.bat" (

    echo Downloading Gradle 8.13...
	echo.
	
    curl -L "https://services.gradle.org/distributions/gradle-8.13-bin.zip" -o gradle.zip
	echo.

    echo Extracting Gradle...
    tar -xf gradle.zip
	echo.

    del gradle.zip

) else (
	echo Gradle is already installed.
	echo.
)

if not exist "jdk-21.0.11\bin\java.exe" (

    echo Downloading Java 21 JDK...
	echo.
	
    curl -L "https://download.oracle.com/java/21/latest/jdk-21_windows-x64_bin.zip" -o jdk.zip
	echo.
	
    echo Extracting Java...
    tar -xf jdk.zip
	echo.
	
    del jdk.zip

) else (
	echo Java is already installed.
	echo.
)


echo Done.
echo.

if not defined NO_PAUSE pause
