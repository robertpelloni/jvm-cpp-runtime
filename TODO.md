# TODO

## Immediate Tasks
- [x] Implement missing primitive accessors (`boolean`, `char`).
- [x] Implement `realloc` and `fillMemory` (memset) in `MemoryAllocator` and `ManagedPointer`.
- [x] Add automated test script (`test.sh` / `test.bat`).
- [x] Implement zero-copy byte array view for `ManagedPointer` without intermediate allocation (`asByteBuffer()`).
- [x] Add multi-threaded stress tests for concurrent memory allocations and pointer operations.
- [x] Implement standard C library string shims in `StdLib.java` (`strlen`, `strcpy`, `strncpy`, `strcmp`, `strncmp`, `strcat`).
- [x] Support aligned pointer operations (`isAligned`, `align`, `aligned_alloc`).
- [x] Support structured layouts (`StructLayout` builder, natural alignment, field offsets).
- [x] Refactor and modularize `TestRuntime` into descriptive, structured test suites.
- [x] Implement memory pooling and sub-allocation strategy in `MemoryAllocator` (`allocatePooled`, `freePooled`).
- [x] Expand `StdLib` with formatted output (`sprintf`) and math routines (`fabs`, `sqrt`, `pow`, etc.).
- [x] Implement benchmarking suite (`BenchmarkRuntime.java`).
- [ ] Explore JDK 21 `MemorySegment` implementation alongside `Unsafe`.
