@echo off
if not exist "org\jvmcpp\runtime\cli" (
    call build.bat
    if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%
)

java -cp . org.jvmcpp.runtime.cli.Main %*
