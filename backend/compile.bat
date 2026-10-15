@echo off
cd /d "%~dp0"
if not exist build mkdir build
javac -d build src\*.java
if errorlevel 1 (
    echo.
    echo Java compilation failed.
    exit /b 1
)
echo.
echo Backend compiled successfully.
