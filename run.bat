@echo off
title LAN Chat Pro
chcp 65001 > nul

set "JAVA_BIN="
if exist "C:\Program Files\Apache NetBeans\jdk\bin\java.exe" set "JAVA_BIN=C:\Program Files\Apache NetBeans\jdk\bin\java.exe"
if exist "%LOCALAPPDATA%\Programs\jdk-21\bin\java.exe" set "JAVA_BIN=%LOCALAPPDATA%\Programs\jdk-21\bin\java.exe"
if not defined JAVA_BIN for /f "delims=" %%i in ('where java.exe 2^>nul') do if not defined JAVA_BIN set "JAVA_BIN=%%i"
if not defined JAVA_BIN set "JAVA_BIN=java"

echo Dang khoi dong LAN Chat Pro...
start "LAN Chat Pro" "%JAVA_BIN%" --enable-native-access=ALL-UNNAMED -jar "%~dp0target\lan-chat-app-1.0.0.jar"
