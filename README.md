# jvm-cpp-runtime

A small Java runtime layer that gives compiled C++ code something to stand
on when it targets the JVM: Java classes that model C++ memory concepts —
pointers with bounds checking, pointer arithmetic, and a managed heap
allocator — so generated/translated C++ can manipulate "native-style"
memory while every access stays bounds-checked and throws instead of
corrupting memory.

**Version:** 0.2.0

## What's implemented

Ten classes in `src/main/java/org/jvmcpp/runtime/` (~65 KB total):

- `ManagedPointer<T>` — a base + offset + bounds pointer over an object;
  typed reads/writes (`readByte`, `readInt`, `readLong`, `readPointer`, …)
  with an explicit bounds check before every access
- `MemoryAccessException` — thrown on out-of-bounds or negative offsets
- `MemoryAllocator` — a managed on-heap allocation layer with pooled
  quota checks
- `CppObject`, `VTable`, `StructLayout` — C++ object model: object
  layout, virtual-table conventions, struct field layout
- `StdLib` — sprintf and math routines for translated code
- `cli/` — a CLI runner for executing translated programs
- `TestRuntime`, `IntegrationTest`, `BenchmarkRuntime` — test/driver
  harness, integration suite, and benchmark runner

There is no C++ compiler in this repo: this is only the Java target
layer for one.

## Building

No build system — plain `javac`:

```bash
./build.sh   # javac -d . src/main/java/org/jvmcpp/runtime/*.java
./test.sh    # runs the test suites
./run.sh     # CLI runner
```

## Status

Small, self-contained experiment, actively developed through November
2026 (last commit 2026-11-07; recent work added the object model,
CLI runner, and integration tests). Companion-in-spirit to
`libjruntime`, which attacks the adjacent problem from the other
direction (Java source → native C++).
