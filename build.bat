@echo off
title LAN Chat Pro - Build Script
chcp 65001 > nul

echo ========================================================
echo   DANG TIEN HANH BIEN DICH VA DONG GOI LAN CHAT PRO...
echo ========================================================

set "JDK_BIN="
if exist "C:\Program Files\Java\jdk-22\bin\javac.exe" (
    set "JDK_BIN=C:\Program Files\Java\jdk-22\bin\"
) else (
    where javac >nul 2>nul
    if %errorlevel% neq 0 (
        echo [LOI] Khong tim thay trinh bien dich javac tren he thong!
        pause
        exit /b 1
    )
)

if not exist "%~dp0lib\flatlaf-3.5.4.jar" (
    echo [1/3] Dang tai thu vien FlatLaf...
    mkdir "%~dp0lib" 2>nul
    powershell -NoProfile -Command "Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/com/formdev/flatlaf/3.5.4/flatlaf-3.5.4.jar' -OutFile '%~dp0lib\flatlaf-3.5.4.jar'"
    powershell -NoProfile -Command "Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/com/formdev/flatlaf-extras/3.5.4/flatlaf-extras-3.5.4.jar' -OutFile '%~dp0lib\flatlaf-extras-3.5.4.jar'"
)

echo [2/3] Dang bien dich cac file ma nguon Java...
if exist "%~dp0target\classes" rmdir /s /q "%~dp0target\classes"
mkdir "%~dp0target\classes" 2>nul

powershell -NoProfile -Command "$src = (Get-ChildItem -Path '%~dp0src\main\java' -Recurse -Filter '*.java').FullName; & '%JDK_BIN%javac.exe' -encoding UTF-8 -cp '%~dp0lib\*' -d '%~dp0target\classes' $src"
if %errorlevel% neq 0 (
    echo [LOI] Bien dich that bai!
    pause
    exit /b 1
)

cd /d "%~dp0target\classes"
"%JDK_BIN%jar.exe" -xf "%~dp0lib\flatlaf-3.5.4.jar"
"%JDK_BIN%jar.exe" -xf "%~dp0lib\flatlaf-extras-3.5.4.jar"
if exist "%~dp0target\classes\module-info.class" del /f /q "%~dp0target\classes\module-info.class"
cd /d "%~dp0"

echo [3/3] Dang dong goi file jar hoan chinh...
(
echo Manifest-Version: 1.0
echo Main-Class: com.lan.chat.Main
echo.
) > "%~dp0target\MANIFEST.MF"

"%JDK_BIN%jar.exe" -cfm "%~dp0target\lan-chat-app-1.0.0.jar" "%~dp0target\MANIFEST.MF" -C "%~dp0target\classes" .

echo.
echo ========================================================
echo   BIEN DICH THANH CONG!
echo   File thuc thi: target\lan-chat-app-1.0.0.jar
echo ========================================================
echo Ban co the chay ngay run.bat hoac run-three-instances.bat.
pause
