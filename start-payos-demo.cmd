@echo off
setlocal
start "SkyGo - Cloudflare and PayOS" "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoExit -ExecutionPolicy Bypass -File "%~dp0scripts\start-payos-demo.ps1" %*
endlocal
