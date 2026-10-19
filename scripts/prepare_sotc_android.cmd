@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0prepare_sotc_android.ps1" %*
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" (
    echo.
    echo A preparacao falhou. Leia a mensagem de erro acima.
    pause
)
exit /b %RESULT%
