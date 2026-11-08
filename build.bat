@echo off
echo [build] Compiling jvm-cpp-runtime...
javac -d . src/main/java/org/jvmcpp/runtime/*.java src/main/java/org/jvmcpp/runtime/cli/*.java
if %ERRORLEVEL% equ 0 (
    if exist "src\main\java\org\jvmcpp\runtime\ffm" (
        echo [build] Compiling FFM preview modules...
        javac --enable-preview --release 21 -d . src/main/java/org/jvmcpp/runtime/ffm/*.java
    )
    echo [build] Build completed successfully.
) else (
    echo [build] Build failed with error %ERRORLEVEL%.
    exit /b %ERRORLEVEL%
)
