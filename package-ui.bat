@echo off
setlocal

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0package-ui.ps1" %*
exit /b %ERRORLEVEL%
