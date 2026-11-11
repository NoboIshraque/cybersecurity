@echo off
rem Mirage - plays the demo by itself and records it (tools\video\DemoVideo.java).
rem   tools\video\run-demo-video.cmd                 build, run, record target\video\mirage-demo.mp4
rem   tools\video\run-demo-video.cmd --compile-only  build and compile only
rem The video is composed from the app windows themselves (no screen recording), so the desktop
rem can be used while it runs. Needs ffmpeg: on the PATH, or set FFMPEG=C:\path\to\ffmpeg.exe.
rem Writes target\video\run\timeline.txt (video time of each step) and a still per step in
rem target\video\run\shots\.
rem Every run (--compile-only too) clears target\video first: copy a finished take out of it.
setlocal
for %%I in ("%~dp0..\..") do set "ROOT=%%~fI"
set "WORK=%ROOT%\target\video"
cd /d "%ROOT%"
if exist "%WORK%" rmdir /s /q "%WORK%"
mkdir "%WORK%\run\shots"
call "%ROOT%\mvnw.cmd" -B -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target\video\cp.txt || exit /b 1
set "DEPS="
for /f "usebackq delims=" %%L in ("%WORK%\cp.txt") do set "DEPS=%%L"
set "CP=%ROOT%\target\classes;%DEPS%"
javac -encoding UTF-8 -cp "%CP%" -d "%WORK%\out" "%ROOT%\tools\video\DemoVideo.java" || exit /b 1
if /i "%~1"=="--compile-only" exit /b 0
pushd "%WORK%\run"
java -cp "%WORK%\out;%CP%" DemoVideo "%WORK%\mirage-demo.mp4"
popd
