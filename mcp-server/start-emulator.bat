@echo off
echo Starting Android emulator (Pixel_9a)...
start "" "%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -avd Pixel_9a -no-snapshot-load

echo Waiting for device to boot...
:wait
timeout /t 3 /nobreak >nul
adb -e shell getprop sys.boot_completed 2>nul | findstr "1" >nul
if errorlevel 1 goto wait

echo.
echo  Emulator ready!
echo.
pause
