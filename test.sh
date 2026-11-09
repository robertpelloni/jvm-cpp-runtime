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

# Run TestCppObject suite
echo "[test] Running TestCppObject..."
java -cp . org.jvmcpp.runtime.TestCppObject

# Run TestCLI suite
echo "[test] Running TestCLI..."
java -cp . org.jvmcpp.runtime.cli.TestCLI

# Run FFM tests if preview modules exist
if [ -f "org/jvmcpp/runtime/ffm/TestFFM.class" ]; then
    echo "[test] Running TestFFM..."
    java --enable-preview -cp . org.jvmcpp.runtime.ffm.TestFFM
fi

echo "[test] Test execution finished successfully."
