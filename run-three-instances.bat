@echo off
title LAN Chat Pro - 3 May Chat Chung (Group Mesh)
chcp 65001 > nul

set "JAVA_BIN="
if exist "C:\Program Files\Apache NetBeans\jdk\bin\java.exe" set "JAVA_BIN=C:\Program Files\Apache NetBeans\jdk\bin\java.exe"
if exist "%LOCALAPPDATA%\Programs\jdk-21\bin\java.exe" set "JAVA_BIN=%LOCALAPPDATA%\Programs\jdk-21\bin\java.exe"
if not defined JAVA_BIN for /f "delims=" %%i in ('where java.exe 2^>nul') do if not defined JAVA_BIN set "JAVA_BIN=%%i"
if not defined JAVA_BIN set "JAVA_BIN=java"

echo [1/3] Dang khoi dong May_A (Port 5000)...
start "LAN Chat - May A" "%JAVA_BIN%" --enable-native-access=ALL-UNNAMED -jar "%~dp0target\lan-chat-app-1.0.0.jar" "May_A" 5000

ping -n 2 127.0.0.1 > nul

echo [2/3] Dang khoi dong May_B (Port 5001)...
start "LAN Chat - May B" "%JAVA_BIN%" --enable-native-access=ALL-UNNAMED -jar "%~dp0target\lan-chat-app-1.0.0.jar" "May_B" 5001

ping -n 2 127.0.0.1 > nul

echo [3/3] Dang khoi dong May_C (Port 5002)...
start "LAN Chat - May C" "%JAVA_BIN%" --enable-native-access=ALL-UNNAMED -jar "%~dp0target\lan-chat-app-1.0.0.jar" "May_C" 5002

echo.
echo ========================================================
echo   DA KHOI DONG 3 MAY CHAT TRONG MANG LAN!
echo   ----------------------------------------
echo   * May_A (Port 5000)
echo   * May_B (Port 5001)
echo   * May_C (Port 5002)
echo.
echo   CAC MAY SE TU DONG DO TIM NHAU QUA UDP BROADCAST!
echo   Hoac ban co the:
echo   - Tu May_B: Nối toi May_A (127.0.0.1:5000)
echo   - Tu May_C: Nối toi May_A (127.0.0.1:5000)
echo   Sau do bat ky ai nhan tin vao 'Phong Chat Chung'
echo   thi ca 3 may deu se nhan duoc dong thoi!
echo ========================================================
pause
