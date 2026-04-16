@echo off
setlocal

set TARGET_DIR=%1
if "%TARGET_DIR%"=="" set TARGET_DIR=EKA2L1
set OVERLAY_DIR=%~dp0

git clone --recurse-submodules https://github.com/EKA2L1/EKA2L1.git "%TARGET_DIR%"
if errorlevel 1 exit /b 1

pushd "%TARGET_DIR%"
git checkout master
if errorlevel 1 exit /b 1

git submodule update --init --recursive
if errorlevel 1 exit /b 1
popd

xcopy "%OVERLAY_DIR%src" "%TARGET_DIR%\src" /E /I /Y
copy /Y "%OVERLAY_DIR%README_ANDROID_STUDIO_OTTER2_2025.2.2_ANALOG.md" "%TARGET_DIR%\README_ANDROID_STUDIO_OTTER2_2025.2.2_ANALOG.md" >nul

echo Overlay applied to %TARGET_DIR%
endlocal
