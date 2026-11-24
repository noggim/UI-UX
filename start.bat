@echo off
rem yongsa_game quick start: runs the game with Gradle (needs JDK 17)
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 if not defined JAVA_HOME (
  echo [ERROR] Java not found. Install JDK 17 and set JAVA_HOME.
  pause
  exit /b 1
)

echo Starting yongsa_game... (first run downloads Gradle/libGDX, may take a while)
call gradlew.bat lwjgl3:run
if errorlevel 1 (
  echo.
  echo [ERROR] Game failed to start. Check the messages above.
  pause
)
