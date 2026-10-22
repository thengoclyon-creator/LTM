@echo off
title LAN Chat Pro - Dual Test Runner
chcp 65001 > nul

set "JAVA_BIN=java"
if exist "C:\Program Files\Apache NetBeans\jdk\bin\javaw.exe" (
    set "JAVA_BIN=C:\Program Files\Apache NetBeans\jdk\bin\javaw.exe"
) else if exist "C:\Program Files\Apache NetBeans\jdk\bin\java.exe" (
    set "JAVA_BIN=C:\Program Files\Apache NetBeans\jdk\bin\java.exe"
)

set "JAR_PATH=%~dp0target\lan-chat-app-1.0.0.jar"
if not exist "%JAR_PATH%" (
    if exist "%~dp0lan-chat-app-1.0.0.jar" (
        set "JAR_PATH=%~dp0lan-chat-app-1.0.0.jar"
    )
)

echo [1/2] Dang khoi dong May_A (Port 5000)...
start "LAN Chat - May A" "%JAVA_BIN%" --enable-native-access=ALL-UNNAMED -jar "%JAR_PATH%" "May_A" 5000

REM Delay 1 second
ping -n 2 127.0.0.1 > nul

echo [2/2] Dang khoi dong May_B (Port 5001)...
start "LAN Chat - May B" "%JAVA_BIN%" --enable-native-access=ALL-UNNAMED -jar "%JAR_PATH%" "May_B" 5001

echo.
echo ========================================================
echo   DA KHOI DONG THANH CONG 2 CUA SO CHAT!
echo   ----------------------------------------
echo   * Cua so 1: May_A dang lang nghe o Port 5000
echo   * Cua so 2: May_B dang lang nghe o Port 5001
echo.
echo   HUONG DAN KET NOI VA CHAT:
echo   1. Tai cua so [May_B]:
echo      - Muc 'Ket noi IP': nhap 127.0.0.1
echo      - Muc 'Port': nhap 5000
echo      - Bam nut [Ket noi TCP]
echo   2. Sau khi co chuong reo, thu nhap tin nhan va emoji!
echo   3. Thu bam nut [Chon File] de gui thu 1 file bat ky.
echo ========================================================
pause
