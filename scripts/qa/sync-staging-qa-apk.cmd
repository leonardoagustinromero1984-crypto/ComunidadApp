@echo off
rem Double-click launcher. The signed APK is built by GitHub Actions.
rem This only syncs the latest successful artifact into apk\.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0sync-staging-qa-apk.ps1"
echo.
pause
