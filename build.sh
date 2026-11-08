#!/bin/bash
set -e

# Compile Java sources
echo "[build] Compiling jvm-cpp-runtime..."
javac -d . src/main/java/org/jvmcpp/runtime/*.java src/main/java/org/jvmcpp/runtime/cli/*.java

# Compile Java 21 preview FFM modules if supported
if [ -d "src/main/java/org/jvmcpp/runtime/ffm" ]; then
    echo "[build] Compiling FFM preview modules..."
    javac --enable-preview --release 21 -d . src/main/java/org/jvmcpp/runtime/ffm/*.java || true
fi

echo "[build] Build completed successfully."
