@echo off
REM
REM Build webview.dll from source for Windows.
REM
REM The webview/webview project publishes pre-built Windows DLLs in GitHub
REM releases. This script can also build from source using MSVC.
REM
REM Prerequisites:
REM   - Visual Studio Build Tools (cl.exe) or Visual Studio with C++ workload
REM   - Git
REM   - NuGet CLI (nuget.exe) on PATH, or it will be downloaded automatically
REM
REM Usage:
REM   scripts\build-webview-windows.bat               Build and install to krema-core\lib\
REM   scripts\build-webview-windows.bat --download     Download pre-built DLL from GitHub releases
REM   scripts\build-webview-windows.bat --clean        Remove build artifacts
REM

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."
set "BUILD_DIR=%PROJECT_DIR%\.webview-build"
set "OUTPUT_DIR=%PROJECT_DIR%\krema-core\lib"
set "WEBVIEW_REPO=https://github.com/webview/webview.git"

REM --- Parse args ---

set "DOWNLOAD=false"
set "CLEAN=false"

:parse_args
if "%~1"=="" goto :end_parse
if "%~1"=="--download" set "DOWNLOAD=true" & shift & goto :parse_args
if "%~1"=="--clean" set "CLEAN=true" & shift & goto :parse_args
if "%~1"=="--help" goto :show_help
if "%~1"=="-h" goto :show_help
echo Unknown option: %~1 >&2
exit /b 1

:show_help
echo Usage: scripts\build-webview-windows.bat [--download] [--clean]
echo.
echo Builds webview.dll from source or downloads a pre-built binary.
echo.
echo Options:
echo   --download   Download pre-built DLL from GitHub releases
echo   --clean      Remove build artifacts and exit
echo.
echo Prerequisites (for building from source):
echo   - Visual Studio Build Tools with C++ workload
echo   - Git
exit /b 0

:end_parse

REM --- Clean ---

if "%CLEAN%"=="true" (
    echo Cleaning build artifacts...
    if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
    echo Done.
    exit /b 0
)

REM --- Download mode ---

if "%DOWNLOAD%"=="true" (
    echo Downloading pre-built webview.dll from GitHub releases...
    if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

    REM Use PowerShell to download the latest release
    powershell -NoProfile -Command ^
        "$release = Invoke-RestMethod -Uri 'https://api.github.com/repos/webview/webview/releases/latest';" ^
        "$asset = $release.assets | Where-Object { $_.name -like 'webview.dll' -or $_.name -like '*windows*x64*.dll' } | Select-Object -First 1;" ^
        "if ($asset) {" ^
        "  Invoke-WebRequest -Uri $asset.browser_download_url -OutFile '%OUTPUT_DIR%\webview.dll';" ^
        "  Write-Host ('Downloaded: ' + $asset.name);" ^
        "} else {" ^
        "  Write-Host 'No pre-built DLL found in latest release. Try building from source instead.' -ForegroundColor Red;" ^
        "  exit 1;" ^
        "}"

    if errorlevel 1 (
        echo Failed to download pre-built DLL.
        echo Try building from source: scripts\build-webview-windows.bat
        exit /b 1
    )

    echo.
    echo Done! webview.dll is ready at: %OUTPUT_DIR%\webview.dll
    exit /b 0
)

REM --- Check dependencies ---

echo Checking build dependencies...

where cl.exe >nul 2>&1
if errorlevel 1 (
    echo Error: cl.exe not found. >&2
    echo Please run this script from a Visual Studio Developer Command Prompt, >&2
    echo or install Visual Studio Build Tools with the C++ workload. >&2
    echo. >&2
    echo You can also use --download to get a pre-built DLL. >&2
    exit /b 1
)

where git.exe >nul 2>&1
if errorlevel 1 (
    echo Error: git not found. Please install Git for Windows. >&2
    exit /b 1
)

echo   All dependencies found.

REM --- Clone or update ---

if exist "%BUILD_DIR%\webview" (
    echo Updating webview source...
    pushd "%BUILD_DIR%\webview"
    git pull --quiet
    popd
) else (
    echo Cloning webview/webview...
    if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"
    git clone --quiet --depth 1 "%WEBVIEW_REPO%" "%BUILD_DIR%\webview"
)

REM --- Download WebView2 SDK via NuGet ---

echo Downloading WebView2 SDK...
set "NUGET_DIR=%BUILD_DIR%\nuget"
if not exist "%NUGET_DIR%" mkdir "%NUGET_DIR%"

where nuget.exe >nul 2>&1
if errorlevel 1 (
    echo   NuGet CLI not found, downloading...
    powershell -NoProfile -Command ^
        "Invoke-WebRequest -Uri 'https://dist.nuget.org/win-x86-commandline/latest/nuget.exe' -OutFile '%NUGET_DIR%\nuget.exe'"
    set "NUGET=%NUGET_DIR%\nuget.exe"
) else (
    set "NUGET=nuget.exe"
)

%NUGET% install Microsoft.Web.WebView2 -OutputDirectory "%NUGET_DIR%" -ExcludeVersion >nul 2>&1
set "WEBVIEW2_DIR=%NUGET_DIR%\Microsoft.Web.WebView2\build\native"
echo   WebView2 SDK ready.

REM --- Build ---

echo Building webview.dll...

set "WEBVIEW_SRC=%BUILD_DIR%\webview"
set "OUTFILE=%BUILD_DIR%\webview.dll"

REM Find the source file
set "SRC_FILE="
if exist "%WEBVIEW_SRC%\webview.cc" set "SRC_FILE=%WEBVIEW_SRC%\webview.cc"
if exist "%WEBVIEW_SRC%\core\src\webview.cc" set "SRC_FILE=%WEBVIEW_SRC%\core\src\webview.cc"

if defined SRC_FILE (
    cl.exe /LD /EHsc /std:c++17 /DWEBVIEW_BUILD_SHARED /DWEBVIEW_MSEDGE ^
        /I"%WEBVIEW_SRC%" ^
        /I"%WEBVIEW_SRC%\core\include" ^
        /I"%WEBVIEW2_DIR%\include" ^
        "%SRC_FILE%" ^
        /Fe:"%OUTFILE%" ^
        /link /LIBPATH:"%WEBVIEW2_DIR%\x64" WebView2LoaderStatic.lib
) else (
    REM Single-header mode
    echo #define WEBVIEW_BUILD_SHARED > "%BUILD_DIR%\webview_wrapper.cc"
    echo #define WEBVIEW_MSEDGE >> "%BUILD_DIR%\webview_wrapper.cc"
    echo #include "webview.h" >> "%BUILD_DIR%\webview_wrapper.cc"

    cl.exe /LD /EHsc /std:c++17 ^
        /I"%WEBVIEW_SRC%" ^
        /I"%WEBVIEW2_DIR%\include" ^
        "%BUILD_DIR%\webview_wrapper.cc" ^
        /Fe:"%OUTFILE%" ^
        /link /LIBPATH:"%WEBVIEW2_DIR%\x64" WebView2LoaderStatic.lib
)

if errorlevel 1 (
    echo.
    echo Build failed. Check the error messages above. >&2
    exit /b 1
)

echo   Built: %OUTFILE%

REM --- Install to project ---

if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"
copy /y "%OUTFILE%" "%OUTPUT_DIR%\webview.dll" >nul
echo   Installed to: %OUTPUT_DIR%\webview.dll

REM Copy WebView2Loader.dll if it exists (needed at runtime if not statically linked)
if exist "%WEBVIEW2_DIR%\x64\WebView2Loader.dll" (
    copy /y "%WEBVIEW2_DIR%\x64\WebView2Loader.dll" "%OUTPUT_DIR%\WebView2Loader.dll" >nul
    echo   Installed WebView2Loader.dll to: %OUTPUT_DIR%\WebView2Loader.dll
)

echo.
echo Done! webview.dll is ready at: %OUTPUT_DIR%\webview.dll

endlocal
