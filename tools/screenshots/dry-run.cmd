@echo off
rem Mirage - GUI dry run on Windows (tools\screenshots\LiveDemoShot.java, see README.md here).
rem Double-click it, or run it from anywhere:
rem   tools\screenshots\dry-run.cmd                 build, compile, run the demo (28 PASS/FAIL checks),
rem                                                 refresh docs\screenshots\live-*.png
rem   tools\screenshots\dry-run.cmd --compile-only  only build and compile, do not start the demo
rem It runs in a fresh target\dryrun folder (LiveDemoShot creates data\ and bait\ where it runs)
rem and exits with LiveDemoShot's code: 0 only when every check passed.
setlocal
for %%I in ("%~dp0..\..") do set "ROOT=%%~fI"
set "WORK=%ROOT%\target\dryrun"
cd /d "%ROOT%"
set "CODE=1"

where javac >nul 2>nul || (
  echo A JDK 17 or newer is required ^(javac not found^): https://adoptium.net
  goto done
)

rem A fresh, empty working folder each time (an old data\ would carry the last run's alerts).
if exist "%WORK%" rmdir /s /q "%WORK%"
if exist "%WORK%" (
  echo Could not clear target\dryrun. Close any dry run that is still open, then try again.
  goto done
)
mkdir "%WORK%"

echo [1/3] Building the classes and reading the classpath (Maven Wrapper) ...
call "%ROOT%\mvnw.cmd" -B -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target\dryrun\cp.txt
if errorlevel 1 (
  echo The Maven build failed. Read the message above.
  goto done
)
set "DEPS="
for /f "usebackq delims=" %%L in ("%WORK%\cp.txt") do set "DEPS=%%L"
set "CP=%ROOT%\target\classes;%DEPS%"

echo [2/3] Compiling LiveDemoShot into target\dryrun\out ...
javac -encoding UTF-8 -cp "%CP%" -d "%WORK%\out" "%ROOT%\tools\screenshots\LiveDemoShot.java"
if errorlevel 1 (
  echo LiveDemoShot did not compile. Read the message above.
  goto done
)

if /i "%~1"=="--compile-only" (
  echo Compiled. Stopping here because of --compile-only; the demo was not started.
  set "CODE=0"
  goto done
)

echo [3/3] Running the dry run: Console, Sensor and Specter windows will open and be clicked through ...
pushd "%WORK%"
java -cp "%WORK%\out;%CP%" LiveDemoShot "%ROOT%\docs\screenshots"
rem FB-08 / FB-05: keep LiveDemoShot's exit code (0 = every check passed).
set "CODE=%ERRORLEVEL%"
popd
if "%CODE%"=="0" echo Dry run passed. Screenshots are in docs\screenshots.

:done
rem On a failure, keep a double-clicked window open so the message can be read.
if not "%CODE%"=="0" (
  echo.
  echo The dry run failed ^(code %CODE%^). Read the message above, then press a key to close.
  pause >nul
)
exit /b %CODE%
