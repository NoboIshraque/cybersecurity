@echo off
rem Mirage - plays the demo by itself for the demo video (tools\video\DemoVideo.java).
rem   tools\video\run-demo-video.cmd     build, compile, open the windows, wait for go.flag
rem Then start the screen recording and create target\video\run\go.flag to start the demo.
rem It writes done.flag when finished; create quit.flag to close everything.
setlocal
for %%I in ("%~dp0..\..") do set "ROOT=%%~fI"
set "WORK=%ROOT%\target\video"
cd /d "%ROOT%"
if exist "%WORK%" rmdir /s /q "%WORK%"
mkdir "%WORK%\run"
call "%ROOT%\mvnw.cmd" -B -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target\video\cp.txt || exit /b 1
set "DEPS="
for /f "usebackq delims=" %%L in ("%WORK%\cp.txt") do set "DEPS=%%L"
set "CP=%ROOT%\target\classes;%DEPS%"
javac -encoding UTF-8 -cp "%CP%" -d "%WORK%\out" "%ROOT%\tools\video\DemoVideo.java" || exit /b 1
if /i "%~1"=="--compile-only" exit /b 0
pushd "%WORK%\run"
java -cp "%WORK%\out;%CP%" DemoVideo
popd
