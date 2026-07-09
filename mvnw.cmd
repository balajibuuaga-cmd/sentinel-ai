@echo off
setlocal

where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  mvn %*
  exit /b %ERRORLEVEL%
)

echo Maven is not installed. On Windows, install Maven or run the project with Docker Compose.
exit /b 1
