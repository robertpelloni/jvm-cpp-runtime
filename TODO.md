# TODO

## Immediate Tasks
- [x] Implement missing primitive accessors (`boolean`, `char`).
- [x] Implement `realloc` and `fillMemory` (memset) in `MemoryAllocator` and `ManagedPointer`.
- [x] Add automated test script (`test.sh` / `test.bat`).
- [ ] Implement zero-copy byte array view for `ManagedPointer` without intermediate allocation.
- [ ] Explore JDK 21 `MemorySegment` implementation alongside `Unsafe`.
- [ ] Add multi-threaded stress tests for concurrent memory allocations and pointer operations.
