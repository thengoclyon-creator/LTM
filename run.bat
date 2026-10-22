@echo off
title LAN Chat Pro
chcp 65001 > nul

set "JAVA_BIN=java"
if exist "C:\Program Files\Apache NetBeans\jdk\bin\javaw.exe" (
    set "JAVA_BIN=C:\Program Files\Apache NetBeans\jdk\bin\javaw.exe"
) else if exist "C:\Program Files\Apache NetBeans\jdk\bin\java.exe" (
    set "JAVA_BIN=C:\Program Files\Apache NetBeans\jdk\bin\java.exe"
)

echo Dang khoi dong LAN Chat Pro...
start "LAN Chat Pro" "%JAVA_BIN%" --enable-native-access=ALL-UNNAMED -jar "%~dp0target\lan-chat-app-1.0.0.jar"
