#!/bin/bash
set -e

# Build if needed
if [ ! -d "org/jvmcpp/runtime/cli" ]; then
    ./build.sh
fi

java -cp . org.jvmcpp.runtime.cli.Main "$@"
