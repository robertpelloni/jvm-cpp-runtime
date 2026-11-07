#!/bin/bash
set -e

# Build first
./build.sh

# Run TestRuntime unit tests
echo "[test] Running TestRuntime..."
java -cp . org.jvmcpp.runtime.TestRuntime

# Run IntegrationTest suite
echo "[test] Running IntegrationTest..."
java -cp . org.jvmcpp.runtime.IntegrationTest

echo "[test] Test execution finished successfully."
