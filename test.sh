#!/bin/bash
set -e

# Build first
./build.sh

# Run TestRuntime
echo "[test] Running TestRuntime..."
java -cp . org.jvmcpp.runtime.TestRuntime
echo "[test] Test execution finished successfully."
