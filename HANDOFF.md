# Session Handoff Log

## Current State Summary
- **Version**: `0.2.0` (sourced from `VERSION.md`).
- **Architecture**: Foundational Java runtime framework for compiling C++ to JVM bytecode, modeling low-level pointers, dynamic memory allocation, and virtual method dispatch with JVM bounds checking.

## Components Implemented & Active
1. **`ManagedPointer<T>`**:
   - Primitive reads/writes: `boolean`, `byte`, `char`, `short`, `int`, `long`, `float`, `double`, 64-bit pointers.
   - Pointer arithmetic & utilities: `addOffset`, `diff` (pointer subtraction), `slice` (bounded sub-ranges with boundary clamping).
   - Alignment operations: `isAligned(alignment)` and `align(alignment)`.
   - Structural abstractions: `Comparable<ManagedPointer<T>>`, `equals`, and `hashCode`.
   - Interop: zero-copy `asByteBuffer()`, direct off-heap native memory tracking (`isDirect`, `getRawAddress()`), and non-mutating `toByteArray()`.
   - Block memory filling: `fillMemory(value, bytes)` (memset).
   - String operations: `readCString()` and `writeCString()`.
   - Deterministic safety: `MemoryAccessException` thrown on out-of-bounds, negative offsets, and boundary crossings.

2. **`MemoryAllocator`**:
   - Multi-tier dynamic allocations: on-heap `malloc`, zero-initialized `calloc`, POSIX `aligned_alloc`, and unmanaged off-heap `mallocDirect`.
   - Memory pooling: `allocatePooled(size)` and `freePooled(pointer)` for recycling small allocations without GC churn.
   - Dynamic resizing: `realloc(pointer, newSize)` with safe data preservation and old allocation sanitization.
   - Resource safety: sanitizing `free(pointer)` with exact block size mapping (`allocatedBlockSizes`, `directAllocations`) preventing double-free or invalid pointer deallocations.
   - Resource constraints: configurable heap quotas (`maxHeapBytes`) throwing `MemoryAccessException` on memory exhaustion.
   - Live metrics: `getActiveAllocationCount()` and `getTotalAllocatedBytes()`.
   - Block memory copy: `copyMemory(dest, src, bytes)` (memcpy).

3. **Compiler Integration Layer**:
   - `StructLayout`: Fluent `Builder` for defining structured C/C++ layouts with natural alignment, field offsets, and typed accessors.
   - `CppObject` & `VTable`: Standard JVM bytecode conventions for C++ class instances with virtual method dispatch:
     - Single and multiple inheritance with parent vtable slot copying and overriding.
     - Subobject base pointer views (`asSubobject`) and this-pointer adjustment thunks (`getCompleteObjectPointer`).
     - Virtual destructor invocation convention at vtable slot 0 on `destroy()`.
     - Pure virtual slot verification.
     - C++ Runtime Type Information (RTTI) and polymorphic casting (`isInstanceOf`, `dynamicCast`, `isSubclassOf`).
     - Operator overloading convention dispatchers (`operatorEquals`, `operatorSubscript`, `operatorBinary`).

4. **Standard C Shims (`StdLib`)**:
   - `<string.h>` operations: `strlen`, `strcpy`, `strncpy`, `strcmp`, `strncmp`, `strcat`, `sprintf`.
   - `<math.h>` operations: `fabs`, `sqrt`, `pow`, `sin`, `cos`, `floor`, `ceil`.
   - `<stdlib.h>` operations: `atoi`, `atol`, `atof`, `rand`, `srand`, `qsort`, `bsearch`.

5. **Modern Java Foreign Function & Memory (FFM) Support**:
   - `FFMMemoryAdapter`: Encapsulates Java 21+ `java.lang.foreign.MemorySegment` and `Arena` for off-heap scoped allocations and aligned segments.
   - `toManagedPointer()`: Bridge from FFM `MemorySegment` to `ManagedPointer`.
   - `TestFFM`: Verification of FFM memory segments, scoped lifetimes, pointer arithmetic, and bulk data copying.

6. **Tooling & Scripts**:
   - CLI runner: `org.jvmcpp.runtime.cli.Main` (`run.sh` / `run.bat`) supporting `--test`, `--integration`, `--all`, `--bench`, `--info`, `--version`, and `--help`.
   - Performance suite: `BenchmarkRuntime.java` (benchmarking primitive read/write, pointer arithmetic stride indexing, malloc/free, pooled alloc/free, allocation scaling across 16B-4KB size tiers, copyMemory memcpy, virtual method dispatch on CppObject/VTable, sprintf formatting, in-place qsort, StructLayout field access, and off-heap direct pointer throughput).
   - Build & test automation: `build.sh`, `build.bat`, `test.sh`, `test.bat`.

## Test & Verification Status
- **Unit Suite (`TestRuntime`)**: 324 assertions passing across 26 modular test suites (including overlapping memory copies/memmove, C-string boundary edge cases, peak watermark tracking, direct off-heap realloc, block size queries, arena freeAll resets, multi-level sub-slice mutation propagation, allocator block tracking metrics across malloc/realloc/free cycles, pointer arithmetic chains, direct memory alignment, IEEE 754 type-punning, odd/single-byte boundary clamping, cross-block heap/direct isolation, and quota exhaustion recovery).
- **Integration Suite (`IntegrationTest`)**: 1,706 assertions passing (heterogeneous heap/direct copyMemory, cyclic dynamic vector resizing, pointer-to-pointer indirection, jagged matrices, high-churn pooling stability, boundary memory checks, slice upper/negative bounds protection, double-free rejection, quota enforcement, contiguous struct arrays, polymorphic dispatch, and concurrent pooling).
- **C++ Object Suite (`TestCppObject`)**: 26 assertions passing (single/multiple inheritance, thunks, virtual destructors, pure virtuals, RTTI isInstanceOf, dynamicCast upcasts and cross-casts, operator==, operator+, operator[]).
- **CLI Suite (`TestCLI`)**: 29 assertions passing (help, version, info, bench, test runners, invalid options).
- **FFM Suite (`TestFFM`)**: Scoped allocation, alignment, and `toManagedPointer` bridge tests passing.
- **Total Assertions**: 1,117+ assertions passing cleanly via `./run.sh --all` and `./test.sh`.

## Governance Documentation
- Fully synchronized: `VERSION.md`, `VISION.md`, `ROADMAP.md`, `TODO.md`, `MEMORY.md`, `DEPLOY.md`, `IDEAS.md`, `CHANGELOG.md`, and `HANDOFF.md`.
