# CHANGELOG

All notable changes to this project will be documented in this file.

## [0.2.0] - 2026-10-22
### Added
- Primitive accessors: `readBoolean`, `writeBoolean`, `readChar`, `writeChar` in `ManagedPointer`.
- Memory utility: `fillMemory` (memset) in `ManagedPointer` and `MemoryAllocator`.
- Memory reallocation: `realloc` in `MemoryAllocator` supporting dynamic block resizing and zero-copy/copy reallocation.
- Build and execution scripts: `test.sh` and `test.bat`.
- Project documentation governance: `VERSION.md`, `ROADMAP.md`, `TODO.md`, `VISION.md`, `MEMORY.md`, `DEPLOY.md`, `IDEAS.md`, `CHANGELOG.md`.
- Expanded test assertions in `TestRuntime` covering all new primitives and memory operations.

## [0.1.0] - 2026-06-25
### Added
- Initial implementation of `ManagedPointer`, `MemoryAllocator`, and `MemoryAccessException`.
- Null-terminated `readCString` and `writeCString`.
- `copyMemory` (memcpy) implementation.
- Basic test harness in `TestRuntime`.
