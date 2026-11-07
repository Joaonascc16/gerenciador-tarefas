@echo off
REM Compila o projeto e roda os testes de ponta a ponta (Windows).
cd /d "%~dp0"
if exist out rmdir /s /q out
if exist out-test rmdir /s /q out-test
mkdir out
mkdir out-test
javac -encoding UTF-8 -d out -sourcepath src\main\java src\main\java\com\gerenciador\Main.java
if errorlevel 1 exit /b 1
xcopy /e /i /y /q src\main\resources out >nul
javac -encoding UTF-8 -cp out -d out-test src\test\java\com\gerenciador\TestesApi.java
if errorlevel 1 exit /b 1
java -cp out;out-test com.gerenciador.TestesApi
