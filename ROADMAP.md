# ROADMAP

## Milestone 1: Core Memory Runtime (Completed)
- [x] Basic pointer representation (`ManagedPointer<T>`)
- [x] Primitive read/write operations (byte, boolean, char, short, int, long, float, double, pointer)
- [x] Memory allocator with `malloc`, `realloc`, and `free`
- [x] Memory pooling strategy (`allocatePooled`, `freePooled`) for fast block recycling
- [x] Memory utilities: `copyMemory` (memcpy) and `fillMemory` (memset)
- [x] Null-terminated C-String read/write support

## Milestone 2: Modern Java Foreign Function & Memory (FFM) Support (Upcoming)
- [x] Support aligned pointer operations (`isAligned`, `align`, `aligned_alloc`).
- [x] Support structured layouts (`StructLayout` builder, natural alignment, field offsets).
- [ ] Migrate/augment `sun.misc.Unsafe` with Java 21+ `java.lang.foreign.MemorySegment` and `Arena` APIs.
- [ ] Implement multi-tier memory allocators (On-Heap byte arrays vs. Off-Heap direct segments).

## Milestone 3: Compiler Integration Layer
- [ ] Define standard JVM bytecode conventions for emitted C++ class instances and vtables.
- [x] Provide lightweight standard C library shims (`<string.h>` and `<math.h>`: strlen, strcpy, strncpy, strcmp, strncmp, strcat, sprintf, fabs, sqrt, pow, sin, cos, floor, ceil) implemented against `ManagedPointer`.
- [x] Add benchmarking suite (`BenchmarkRuntime.java`).
- [ ] Add CLI runner.
