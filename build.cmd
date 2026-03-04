@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"

set "TARGET=%~1"
if "%TARGET%"=="" set "TARGET=all"

set "CONFIG=%~2"
if "%CONFIG%"=="" set "CONFIG=Release"

set "RELEASE_DIR=%ROOT_DIR%\release"
if not exist "%RELEASE_DIR%" mkdir "%RELEASE_DIR%"

if /I "%TARGET%"=="ui" goto :build_ui
if /I "%TARGET%"=="vst" goto :build_vst
if /I "%TARGET%"=="all" goto :build_all
goto :usage

:build_all
call :build_ui || exit /b 1
call :build_vst || exit /b 1
goto :eof

:build_ui
echo [TamTalk] Building UI (Android debug APK)...
pushd "%ROOT_DIR%\UI\moble\android" || exit /b 1
call gradlew.bat :app:assembleDebug || (popd & exit /b 1)
popd

set "APK_SRC=%ROOT_DIR%\UI\moble\android\app\build\outputs\apk\debug\app-debug.apk"
set "UI_RELEASE_DIR=%RELEASE_DIR%\ui"
if not exist "%UI_RELEASE_DIR%" mkdir "%UI_RELEASE_DIR%"

if exist "%APK_SRC%" (
  copy /Y "%APK_SRC%" "%UI_RELEASE_DIR%\tamtalk-android-debug.apk" >nul
  echo [TamTalk] UI artifact copied: release\ui\tamtalk-android-debug.apk
) else (
  echo [TamTalk] Warning: APK not found at expected path: %APK_SRC%
)
exit /b 0

:build_vst
echo [TamTalk] Building VST3 (%CONFIG%)...
set "VST_DIR=%ROOT_DIR%\VST\vst3-plugin"

set "CMAKE_CMD=cmake"
where cmake >nul 2>nul
if errorlevel 1 (
  if exist "C:\Program Files\CMake\bin\cmake.exe" set "CMAKE_CMD=C:\Program Files\CMake\bin\cmake.exe"
)

set "CMAKE_GENERATOR_LOCAL=%CMAKE_GENERATOR%"
if "%CMAKE_GENERATOR_LOCAL%"=="" set "CMAKE_GENERATOR_LOCAL=NMake Makefiles"

if /I "%CMAKE_GENERATOR_LOCAL%"=="NMake Makefiles" (
  if exist "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat" (
    call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat" -arch=x64 -host_arch=x64 || exit /b 1
  ) else if exist "C:\Program Files\Microsoft Visual Studio\2022\Community\Common7\Tools\VsDevCmd.bat" (
    call "C:\Program Files\Microsoft Visual Studio\2022\Community\Common7\Tools\VsDevCmd.bat" -arch=x64 -host_arch=x64 || exit /b 1
  )
)

set "BUILD_DIR=%VST_DIR%\build"
if /I "%CMAKE_GENERATOR_LOCAL%"=="NMake Makefiles" set "BUILD_DIR=%VST_DIR%\build-nmake"
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"

set "JUCE_DIR_ARG="
if defined JUCE_DIR (
  set "JUCE_DIR_ARG=-DJUCE_DIR=%JUCE_DIR%"
) else if exist "%ROOT_DIR%\VST\JUCE\CMakeLists.txt" (
  set "JUCE_DIR_ARG=-DJUCE_DIR=%ROOT_DIR%\VST\JUCE"
)

"%CMAKE_CMD%" -S "%VST_DIR%" -B "%BUILD_DIR%" -G "%CMAKE_GENERATOR_LOCAL%" %JUCE_DIR_ARG% || exit /b 1
"%CMAKE_CMD%" --build "%BUILD_DIR%" --config "%CONFIG%" || exit /b 1

set "VST_RELEASE_DIR=%RELEASE_DIR%\vst"
if not exist "%VST_RELEASE_DIR%" mkdir "%VST_RELEASE_DIR%"

set "COPIED_ANY="
for /f "delims=" %%D in ('dir /b /s /ad "%BUILD_DIR%\*.vst3" 2^>nul') do (
  set "VST_NAME=%%~nxD"
  rmdir /s /q "%VST_RELEASE_DIR%\!VST_NAME!" 2>nul
  xcopy /E /I /Y "%%D" "%VST_RELEASE_DIR%\!VST_NAME!\" >nul
  echo [TamTalk] VST artifact copied: release\vst\!VST_NAME!
  set "COPIED_ANY=1"
)

if not defined COPIED_ANY (
  for /f "delims=" %%D in ('dir /b /s /ad "%ROOT_DIR%\VST\vst3-plugin\build-nmake\TamTalkVST3_artefacts\*.vst3" 2^>nul') do (
    set "VST_NAME=%%~nxD"
    rmdir /s /q "%VST_RELEASE_DIR%\!VST_NAME!" 2>nul
    xcopy /E /I /Y "%%D" "%VST_RELEASE_DIR%\!VST_NAME!\" >nul
    echo [TamTalk] VST artifact copied: release\vst\!VST_NAME!
    set "COPIED_ANY=1"
  )
)

if not defined COPIED_ANY (
  echo [TamTalk] Warning: no .vst3 artifact found.
)

exit /b 0

:usage
echo Usage: build.cmd [ui^|vst^|all] [Debug^|Release]
echo Examples:
echo   build.cmd ui
echo   build.cmd vst Release
echo   set JUCE_DIR=C:\dev\JUCE ^&^& build.cmd vst Release
echo   set CMAKE_GENERATOR=Visual Studio 17 2022 ^&^& build.cmd vst Release
exit /b 1
