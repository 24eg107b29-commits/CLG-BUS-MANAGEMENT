@echo off
cd /d "%~dp0"
if not exist build (
    echo Build directory not found. Compiling first...
    call compile.bat
    if errorlevel 1 exit /b 1
)
if not exist lib\mysql-connector-j-*.jar (
    echo.
    echo MySQL Connector/J is required in backend\lib.
    echo Download it from https://dev.mysql.com/downloads/connector/j/
    echo and place the JAR inside backend\lib before running the app.
    exit /b 1
)
java -cp "build;lib\*" Main
