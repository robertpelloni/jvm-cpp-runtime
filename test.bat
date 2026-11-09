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

echo [test] Running TestCppObject...
java -cp . org.jvmcpp.runtime.TestCppObject
if %ERRORLEVEL% neq 0 (
    echo [test] TestCppObject failed with error %ERRORLEVEL%.
    exit /b %ERRORLEVEL%
)

echo [test] Running TestCLI...
java -cp . org.jvmcpp.runtime.cli.TestCLI
if %ERRORLEVEL% neq 0 (
    echo [test] TestCLI failed with error %ERRORLEVEL%.
    exit /b %ERRORLEVEL%
)

if exist "org\jvmcpp\runtime\ffm\TestFFM.class" (
    echo [test] Running TestFFM...
    java --enable-preview -cp . org.jvmcpp.runtime.ffm.TestFFM
    if %ERRORLEVEL% neq 0 (
        echo [test] FFM tests failed with error %ERRORLEVEL%.
        exit /b %ERRORLEVEL%
    )
)

echo [test] Test execution finished successfully.
