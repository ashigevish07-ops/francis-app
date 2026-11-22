@echo off
title FRANCIS OPTIMIZE - MSI APP PLAYER & BLUESTACKS ROOT + 240 FPS UNLOCKER
color 0F
cls
echo ====================================================================
echo   FRANCIS OPTIMIZE - MSI / BLUESTACKS ROOT + HIGH FPS UNLOCKER
echo ====================================================================
echo.
echo [*] Make sure MSI App Player / BlueStacks is COMPLETELY CLOSED first!
echo.
pause

set "CONF="
if exist "C:\ProgramData\BlueStacks_msi5\bluestacks.conf" set "CONF=C:\ProgramData\BlueStacks_msi5\bluestacks.conf"
if "%CONF%"=="" if exist "C:\ProgramData\BlueStacks_nxt\bluestacks.conf" set "CONF=C:\ProgramData\BlueStacks_nxt\bluestacks.conf"
if "%CONF%"=="" goto NO_CONF

echo [*] Found config: %CONF%
copy /Y "%CONF%" "%CONF%.francis_backup" >nul
echo [*] Backup saved: %CONF%.francis_backup
echo [*] Enabling Root Access + High FPS (240 FPS)...

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$p = '%CONF%';" ^
  "$c = Get-Content -Raw $p;" ^
  "$c = [regex]::Replace($c, 'bst\.feature\.rooting=\"0\"', 'bst.feature.rooting=\"1\"');" ^
  "$c = [regex]::Replace($c, 'enable_root_access=\"0\"', 'enable_root_access=\"1\"');" ^
  "$c = [regex]::Replace($c, 'enable_high_fps=\"0\"', 'enable_high_fps=\"1\"');" ^
  "$c = [regex]::Replace($c, 'max_fps=\"\d+\"', 'max_fps=\"240\"');" ^
  "Set-Content -NoNewline -Path $p -Value $c"

if errorlevel 1 goto FAIL

echo.
echo ====================================================================
echo   [SUCCESS] Root Access + 240 FPS Unlocked!
echo   Now open MSI App Player / BlueStacks and install FRANCIS-OPTIMIZE.apk
echo ====================================================================
echo.
pause
exit /b 0

:NO_CONF
echo [ERROR] bluestacks.conf not found in C:\ProgramData\BlueStacks_msi5 or BlueStacks_nxt!
echo Run this script as Administrator if needed.
echo.
pause
exit /b 1

:FAIL
echo [ERROR] Could not write bluestacks.conf. Right-click this file -> Run as Administrator!
echo.
pause
exit /b 1
