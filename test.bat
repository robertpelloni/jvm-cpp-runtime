@echo off
call build.bat
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

echo [test] Running TestRuntime...
java -cp . org.jvmcpp.runtime.TestRuntime
if %ERRORLEVEL% equ 0 (
    echo [test] Test execution finished successfully.
) else (
    echo [test] Tests failed with error %ERRORLEVEL%.
    exit /b %ERRORLEVEL%
)
