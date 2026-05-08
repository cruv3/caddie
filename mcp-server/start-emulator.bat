@echo off
REM Start the Android emulator and wire up the HTTP-bridge port forward.
REM Usage:
REM   start-emulator.bat              -> uses default AVD "Pixel_9a"
REM   start-emulator.bat Pixel_8_Pro  -> uses given AVD name

setlocal

set "AVD_NAME=%~1"
if "%AVD_NAME%"=="" set "AVD_NAME=Pixel_9a"

set "EMULATOR_DIR=%LOCALAPPDATA%\Android\Sdk\emulator"
set "PLATFORM_TOOLS=%LOCALAPPDATA%\Android\Sdk\platform-tools"

if not exist "%EMULATOR_DIR%\emulator.exe" (
    echo [start-emulator] emulator.exe not found at %EMULATOR_DIR%
    echo Set ANDROID_HOME or install Android SDK.
    exit /b 1
)
if not exist "%PLATFORM_TOOLS%\adb.exe" (
    echo [start-emulator] adb.exe not found at %PLATFORM_TOOLS%
    exit /b 1
)

echo [start-emulator] Booting AVD "%AVD_NAME%" (cold boot, no snapshot)...
start "Android Emulator" /B "%EMULATOR_DIR%\emulator.exe" -avd "%AVD_NAME%" -no-snapshot-load -audio host

echo [start-emulator] Waiting for device to become ready...
"%PLATFORM_TOOLS%\adb.exe" wait-for-device
if errorlevel 1 (
    echo [start-emulator] adb wait-for-device failed.
    exit /b 1
)

REM Block until boot completes (sys.boot_completed=1 means UI is up).
:wait_boot
for /f "tokens=*" %%b in ('"%PLATFORM_TOOLS%\adb.exe" shell getprop sys.boot_completed 2^>nul') do set "BOOT=%%b"
if not "%BOOT%"=="1" (
    timeout /t 2 /nobreak >nul
    goto wait_boot
)

echo [start-emulator] Device booted. Setting up port forward 8765 -^> 8765...
"%PLATFORM_TOOLS%\adb.exe" forward tcp:8765 tcp:8765
if errorlevel 1 (
    echo [start-emulator] adb forward failed.
    exit /b 1
)

echo [start-emulator] Setting up reverse tunnel for overlay observer (8787)...
"%PLATFORM_TOOLS%\adb.exe" reverse tcp:8787 tcp:8787
if errorlevel 1 (
    echo [start-emulator] adb reverse failed (overlay pill won't get events).
)

echo.
echo [start-emulator] Ready.
echo   AVD:         %AVD_NAME%
echo   HTTP bridge: http://127.0.0.1:8765 (host -^> device)
echo   Agent SSE:   http://127.0.0.1:8787 (device -^> host, overlay observer)
echo.
echo Tip: make sure the LLMSmartphone Android app is installed and its
echo      Accessibility Service is enabled before calling MCP tools.

endlocal
