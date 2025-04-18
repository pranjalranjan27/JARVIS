:: Auto-start launcher for Jarvis backend and frontend
:: Starts local API server and opens UI automatically

@echo off
setlocal
echo ══════════════════════════════════════════
echo   Building J.A.R.V.I.S...
echo ══════════════════════════════════════════
set "BUILD_DIR=%TEMP%\jarvis-build"
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
mkdir "%BUILD_DIR%"
javac -cp "lib\sqlite-jdbc-3.45.1.0.jar;lib\slf4j-api-1.7.36.jar;lib\json-20240303.jar" -d "%BUILD_DIR%" -sourcepath src\main\java src\main\java\com\jarvis\JarvisApp.java src\main\java\com\jarvis\db\DatabaseManager.java src\main\java\com\jarvis\engine\InputProcessor.java src\main\java\com\jarvis\engine\IntentDetector.java src\main\java\com\jarvis\engine\ConversationContext.java src\main\java\com\jarvis\engine\OppositionEngine.java src\main\java\com\jarvis\engine\ResponseGenerator.java src\main\java\com\jarvis\engine\CommandExecutor.java src\main\java\com\jarvis\llm\LLMService.java src\main\java\com\jarvis\llm\OllamaService.java src\main\java\com\jarvis\api\ApiServer.java
if %ERRORLEVEL% NEQ 0 (
    echo BUILD FAILED
    pause
    rmdir /s /q "%BUILD_DIR%"
    endlocal
    exit /b 1
)
echo Build successful!
echo ══════════════════════════════════════════
echo   Launching J.A.R.V.I.S...
echo ══════════════════════════════════════════
REM Start Ollama in background (safe to run even if already running)

:: Start backend API server
start "" /B ollama serve

REM Start backend silently — JarvisApp will wait for Ollama internally
start "" /B java -cp "%BUILD_DIR%;lib\sqlite-jdbc-3.45.1.0.jar;lib\slf4j-api-1.7.36.jar;lib\slf4j-simple-1.7.36.jar;lib\json-20240303.jar" com.jarvis.JarvisApp

REM Wait for Ollama + Jarvis to be ready (JarvisApp retries Ollama for up to 15s)
timeout /t 18 /nobreak >nul

REM Open browser

:: Launch Jarvis frontend UI
start "" "http://localhost:8080"
echo Jarvis is running. Close this window to stop the backend.
endlocal
