@echo off
REM Compila e executa o Gerenciador de Tarefas (Windows). Requer JDK 17 ou superior.
REM Uso: executar.bat [porta]
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
javac -encoding UTF-8 -d out -sourcepath src\main\java src\main\java\com\gerenciador\Main.java
if errorlevel 1 exit /b 1
xcopy /e /i /y /q src\main\resources out >nul
java -cp out com.gerenciador.Main %*
