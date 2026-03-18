@echo off
setlocal

rem Define the drive letter to use (e.g., X:)
set DRIVE_LETTER=X:

rem Get the current directory (project root)
set PROJECT_ROOT=%~dp0
rem Remove trailing backslash
if %PROJECT_ROOT:~-1%==\ set PROJECT_ROOT=%PROJECT_ROOT:~0,-1%

echo ========================================================
echo  Windows Max Path Workaround Build Script
echo ========================================================
echo.
echo Project Root: %PROJECT_ROOT%
echo Mounting to drive: %DRIVE_LETTER%
echo.

rem Unmount if already exists (just in case)
subst %DRIVE_LETTER% /d >nul 2>&1

rem Mount the project to the drive letter
subst %DRIVE_LETTER% "%PROJECT_ROOT%"
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to mount drive %DRIVE_LETTER%. It might be in use.
    echo Try changing the drive letter in this script.
    pause
    exit /b 1
)

echo Drive mounted successfully.
echo Switching to virtual drive...

rem Switch to the Android directory on the virtual drive
%DRIVE_LETTER%
cd \android

echo.
echo ========================================================
echo  Cleaning Project...
echo ========================================================
call gradlew.bat clean

echo.
echo ========================================================
echo  Building Debug APK...
echo ========================================================
call gradlew.bat assembleDebug

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Build Failed!
    echo Unmounting drive...
    cd /d "%PROJECT_ROOT%"
    subst %DRIVE_LETTER% /d
    pause
    exit /b 1
)

echo.
echo ========================================================
echo  Build Success!
echo ========================================================
echo.
echo The APK should be located at:
echo %PROJECT_ROOT%\android\app\build\outputs\apk\debug\app-debug.apk
echo.

echo Unmounting drive...
cd /d "%PROJECT_ROOT%"
subst %DRIVE_LETTER% /d

echo Done.
pause
