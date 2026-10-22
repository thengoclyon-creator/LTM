@echo off
title Push Nhanh Khuong Len GitHub
chcp 65001 > nul

set "PATH=%LOCALAPPDATA%\Programs\MinGit\cmd;%LOCALAPPDATA%\Programs\MinGit\mingw64\bin;%PATH%"

echo ========================================================
echo   DANG DAY NHANH [Khuong] LEN GITHUB REPOSITORY...
echo   https://github.com/thengoclyon-creator/LTM.git
echo ========================================================
echo.

git push -u origin Khuong

if %errorlevel% equ 0 (
    echo.
    echo ========================================================
    echo   [THANH CONG] Nhanh Khuong da duoc tao va day len GitHub!
    echo ========================================================
) else (
    echo.
    echo ========================================================
    echo   [CHU Y] Neu trinh duyet hien cua so xac thuc GitHub,
    echo   hay dang nhap tai khoan co quyen ghi (write permission).
    echo ========================================================
)
echo.
pause
