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
- [x] Implement multi-tier memory allocators (On-Heap byte arrays vs. Off-Heap direct segments via `mallocDirect`).
- [x] Migrate/augment `sun.misc.Unsafe` with Java 21+ `java.lang.foreign.MemorySegment` and `Arena` APIs (`FFMMemoryAdapter.java`, `toManagedPointer` bridge, `TestFFM.java`).

## Milestone 3: Compiler Integration Layer
- [x] Define standard JVM bytecode conventions for emitted C++ class instances and vtables (`CppObject`, `VTable`).
- [x] Support single/multiple inheritance vtable slot copying, method overriding, subobject slicing, and this-pointer thunks (`asSubobject`, `getCompleteObjectPointer`).
- [x] Support standard virtual destructor conventions at vtable slot 0.
- [x] Support C++ Runtime Type Information (RTTI) and polymorphic casting (`isInstanceOf`, `dynamicCast`, `isSubclassOf`).
- [x] Support C++ operator overloading convention helpers (`operatorEquals`, `operatorSubscript`, `operatorBinary`).
- [x] Provide lightweight standard C library shims (`<string.h>`, `<math.h>`, `<stdlib.h>`: strlen, strcpy, strncpy, strcmp, strncmp, strcat, sprintf, fabs, sqrt, pow, sin, cos, floor, ceil, atoi, atol, atof, rand, srand, qsort, bsearch) implemented against `ManagedPointer`.
- [x] Add benchmarking suite (`BenchmarkRuntime.java`).
- [x] Add CLI runner (`org.jvmcpp.runtime.cli.Main`, `run.sh`, `run.bat`) and CLI test suite (`TestCLI.java`).
