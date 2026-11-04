# DEPLOY

## Requirements
- Java Development Kit (JDK) 21 or higher.

## Building
Compile all runtime sources directly using the provided build scripts:
```bash
./build.sh      # Linux / macOS
build.bat       # Windows
```

## Running Tests
Run the runtime test harness:
```bash
./test.sh       # Linux / macOS
test.bat        # Windows
```

Alternatively, invoke via standard java tools:
```bash
java -cp . org.jvmcpp.runtime.TestRuntime
```
