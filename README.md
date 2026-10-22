# jvm-cpp-runtime

A small Java runtime layer that gives compiled C++ code something to stand
on when it targets the JVM: Java classes that model C++ memory concepts —
pointers with bounds checking, pointer arithmetic, and a managed heap
allocator — so generated/translated C++ can manipulate "native-style"
memory while every access stays bounds-checked and throws instead of
corrupting memory.

## What's implemented

Four classes in `src/main/java/org/jvmcpp/runtime/` (~12 KB total):

- `ManagedPointer<T>` — a base + offset + bounds pointer over an object;
  typed reads/writes (`readByte`, `readInt`, `readLong`, `readPointer`, …)
  with an explicit bounds check before every access
- `MemoryAccessException` — thrown on out-of-bounds or negative offsets
- `MemoryAllocator` — a managed on-heap allocation layer
- `TestRuntime` — a test/driver harness for the above

Per `HANDOFF.md`, the classes were merged in from the `jvmcpp-runtime`
branch and are the integrated state of the project. The README this
replaces was mostly the LLM prompt used to generate the code; the code
itself is what's described above — no more, no less. There is no C++
compiler in this repo: this is only the Java target layer for one.

## Building

No build system — plain `javac`:

```bash
./build.sh   # javac -d . src/main/java/org/jvmcpp/runtime/*.java
```

## Status

Tiny, self-contained experiment from June 2026 (latest commit is an
automated merge checkpoint, October 2026). Companion-in-spirit to
`libjruntime`, which attacks the adjacent problem from the other direction
(Java source → native C++).
