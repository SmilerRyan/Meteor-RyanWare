@echo off
cls
cd /d "%~dp0"
cmd /c gradlew clean build

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd-HH-mm-ss"') do set "TS=%%i"

::copy /Y "%cd%\build\libs\meteor-RyanWare-0.1.jar" "%cd%\RyanWare-%TS%.jar"

move "%cd%\build\libs\meteor-RyanWare-0.1.jar" "%cd%\build\libs\RyanWare-%TS%.jar"

pause