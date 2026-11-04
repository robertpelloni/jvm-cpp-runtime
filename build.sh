#!/bin/bash
set -e

# Compile Java sources
echo "[build] Compiling jvm-cpp-runtime..."
javac -d . src/main/java/org/jvmcpp/runtime/*.java
echo "[build] Build completed successfully."
