@echo off
title LAN Chat Pro
chcp 65001 > nul

echo ========================================================
echo       KHOI DONG LAN CHAT PRO - MANG NOI BO
echo ========================================================
echo.

set "JAVA_BIN="

REM 1. Kiem tra JAVA_HOME neu co
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVA_BIN=%JAVA_HOME%\bin\javaw.exe"
    if not defined JAVA_BIN if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
)

REM 2. Kiem tra JDK NetBeans (neu co)
if not defined JAVA_BIN (
    if exist "C:\Program Files\Apache NetBeans\jdk\bin\javaw.exe" (
        set "JAVA_BIN=C:\Program Files\Apache NetBeans\jdk\bin\javaw.exe"
    ) else if exist "C:\Program Files\Apache NetBeans\jdk\bin\java.exe" (
        set "JAVA_BIN=C:\Program Files\Apache NetBeans\jdk\bin\java.exe"
    )
)

REM 3. Kiem tra thu muc cai dat Java mac dinh tren Windows
if not defined JAVA_BIN (
    for /d %%D in ("C:\Program Files\Java\jdk*" "C:\Program Files\Eclipse Adoptium\jdk*" "C:\Program Files\Microsoft\jdk*" "C:\Program Files\BellSoft\*") do (
        if exist "%%D\bin\javaw.exe" set "JAVA_BIN=%%D\bin\javaw.exe"
        if not defined JAVA_BIN if exist "%%D\bin\java.exe" set "JAVA_BIN=%%D\bin\java.exe"
    )
)

REM 4. Kiem tra java trong bien moi truong PATH
if not defined JAVA_BIN (
    where javaw >nul 2>&1
    if not errorlevel 1 set "JAVA_BIN=javaw"
)
if not defined JAVA_BIN (
    where java >nul 2>&1
    if not errorlevel 1 set "JAVA_BIN=java"
)

REM 5. Neu van khong tim thay Java
if not defined JAVA_BIN (
    echo [LOI] Khong tim thay Java JDK tren may tinh nay!
    echo.
    echo Vui long cai dat Java (JDK 17 tro len) tai:
    echo   https://adoptium.net/ hoac https://www.oracle.com/java/technologies/downloads/
    echo Sau khi cai dat, hay chay lai file nay.
    echo.
    pause
    exit /b 1
)

REM Tim file JAR ung dung
set "JAR_FILE=%~dp0target\lan-chat-app-1.0.0.jar"
if not exist "%JAR_FILE%" (
    set "JAR_FILE=%~dp0lan-chat-app-1.0.0.jar"
)

if not exist "%JAR_FILE%" (
    echo [LOI] Khong tim thay file ung dung: %JAR_FILE%
    echo Vui long kiem tra file 'lan-chat-app-1.0.0.jar' trong thu muc.
    echo.
    pause
    exit /b 1
)

echo [OK] Su dung Java: %JAVA_BIN%
echo [OK] File ung dung: %JAR_FILE%
echo.
echo [!] LUU Y MANG LAN:
echo     Neu Windows Firewall hien thong bao, hay chon 'Allow access / Cho phep'
echo     de cac may trong mang LAN co the tu dong tim thay nhau!
echo.
echo Dang mo ung dung...

start "LAN Chat Pro" "%JAVA_BIN%" --enable-native-access=ALL-UNNAMED -jar "%JAR_FILE%" %*

timeout /t 2 >nul
exit /b 0
