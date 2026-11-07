@echo off
call build.bat
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

echo [test] Running TestRuntime...
java -cp . org.jvmcpp.runtime.TestRuntime
if %ERRORLEVEL% neq 0 (
    echo [test] Unit tests failed with error %ERRORLEVEL%.
    exit /b %ERRORLEVEL%
)

echo [test] Running IntegrationTest...
java -cp . org.jvmcpp.runtime.IntegrationTest
if %ERRORLEVEL% neq 0 (
    echo [test] Integration tests failed with error %ERRORLEVEL%.
    exit /b %ERRORLEVEL%
)

echo [test] Test execution finished successfully.
