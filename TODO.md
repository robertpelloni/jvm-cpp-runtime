# TODO

## Immediate Tasks
- [x] Implement missing primitive accessors (`boolean`, `char`).
- [x] Implement `realloc` and `fillMemory` (memset) in `MemoryAllocator` and `ManagedPointer`.
- [x] Add automated test script (`test.sh` / `test.bat`).
- [x] Implement zero-copy byte array view for `ManagedPointer` without intermediate allocation (`asByteBuffer()`).
- [x] Add multi-threaded stress tests for concurrent memory allocations and pointer operations.
- [ ] Explore JDK 21 `MemorySegment` implementation alongside `Unsafe`.
