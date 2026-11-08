@echo off
rem Mirage - Laptop A (defender): Console + Sensor (decoys) in one process.
rem Put mirage.jar (or build it: mvnw.cmd -DskipTests package) and mirage.properties next to this script.
setlocal
cd /d "%~dp0"
set "JAR=%~dp0mirage.jar"
if not exist "%JAR%" set "JAR=%~dp0target\mirage.jar"
if not exist "%JAR%" (
  echo mirage.jar not found. Build it with:  mvnw.cmd -DskipTests package
  exit /b 1
)
where java >nul 2>nul || (
  echo Java 17 or newer is required: https://adoptium.net
  exit /b 1
)
if not exist mirage.properties echo No mirage.properties here: running the single-machine defaults ^(127.0.0.1^).
java -jar "%JAR%" defender %*
rem FB-08: on a failure, keep a double-clicked window open so the message can be read.
set "CODE=%ERRORLEVEL%"
if not "%CODE%"=="0" (
  echo.
  echo Mirage exited with an error ^(code %CODE%^). Read the message above, then press a key to close.
  pause >nul
)
exit /b %CODE%
