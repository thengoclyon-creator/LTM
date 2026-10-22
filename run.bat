@echo off
title LAN Chat Pro
chcp 65001 > nul

echo ========================================================
echo       KHOI DONG LAN CHAT PRO - MANG NOI BO
echo ========================================================
echo.

set "JAVA_BIN="

REM 1. Uu tien tim trong cac thu muc cai dat thuc te
if exist "C:\Program Files\Apache NetBeans\jdk\bin\javaw.exe" (
    set "JAVA_BIN=C:\Program Files\Apache NetBeans\jdk\bin\javaw.exe"
)
if not defined JAVA_BIN if exist "C:\Program Files\Apache NetBeans\jdk\bin\java.exe" (
    set "JAVA_BIN=C:\Program Files\Apache NetBeans\jdk\bin\java.exe"
)
if not defined JAVA_BIN if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVA_BIN=%JAVA_HOME%\bin\javaw.exe"
    if not defined JAVA_BIN if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
)
if not defined JAVA_BIN (
    for /d %%D in ("C:\Program Files\Java\jdk*" "C:\Program Files\Eclipse Adoptium\jdk*" "C:\Program Files\Microsoft\jdk*") do (
        if exist "%%D\bin\javaw.exe" set "JAVA_BIN=%%D\bin\javaw.exe"
        if not defined JAVA_BIN if exist "%%D\bin\java.exe" set "JAVA_BIN=%%D\bin\java.exe"
    )
)

REM 2. Lay duong dan day du tu where.exe neu co
if not defined JAVA_BIN (
    for /f "delims=" %%I in ('where javaw 2^>nul') do (
        if not defined JAVA_BIN set "JAVA_BIN=%%I"
    )
)
if not defined JAVA_BIN (
    for /f "delims=" %%I in ('where java 2^>nul') do (
        if not defined JAVA_BIN set "JAVA_BIN=%%I"
    )
)

REM 3. Fallback
if not defined JAVA_BIN set "JAVA_BIN=java"

REM Tim file JAR
set "JAR_FILE=%~dp0target\lan-chat-app-1.0.0.jar"
if not exist "%JAR_FILE%" set "JAR_FILE=%~dp0lan-chat-app-1.0.0.jar"

if not exist "%JAR_FILE%" (
    echo [LOI] Khong tim thay file ung dung lan-chat-app-1.0.0.jar!
    echo Vui long kiem tra file jar trong thu muc.
    pause
    exit /b 1
)

echo [OK] Su dung Java: %JAVA_BIN%
echo [OK] File ung dung: %JAR_FILE%
echo.
echo [!] LUU Y: Neu Windows Firewall hoi, hay chon 'Allow access / Cho phep'
echo     de cac may trong mang LAN ket noi duoc voi nhau.
echo.
echo Dang khoi dong giao dien chat LAN Chat Pro...

start "LAN Chat Pro" "%JAVA_BIN%" --enable-native-access=ALL-UNNAMED -jar "%JAR_FILE%" %*
