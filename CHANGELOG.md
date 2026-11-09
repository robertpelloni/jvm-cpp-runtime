# CHANGELOG

All notable changes to this project will be documented in this file.

## [0.2.0] - 2026-10-22
### Added
- Primitive accessors: `readBoolean`, `writeBoolean`, `readChar`, `writeChar` in `ManagedPointer`.
- Memory utility: `fillMemory` (memset) in `ManagedPointer` and `MemoryAllocator`.
- Memory reallocation: `realloc` in `MemoryAllocator` supporting dynamic block resizing and zero-copy/copy reallocation.
- Standard C Library Shims (`org.jvmcpp.runtime.StdLib`): `<string.h>` (`strlen`, `strcpy`, `strncpy`, `strcmp`, `strncmp`, `strcat`, `sprintf`), `<math.h>` (`fabs`, `sqrt`, `pow`, `sin`, `cos`, `floor`, `ceil`), `<stdlib.h>` (`atoi`, `atol`, `atof`, `rand`, `srand`, `qsort`, `bsearch`).
- C++ class instance layout and vtable dispatch primitives (`CppObject`, `VTable`): single and multiple inheritance, parent vtable slot copying, method overriding, subobject slicing, this-pointer thunks (`asSubobject`, `getCompleteObjectPointer`), virtual destructors at slot 0, and comprehensive test suite (`TestCppObject`).
- Unified CLI runner (`org.jvmcpp.runtime.cli.Main`, `run.sh`, `run.bat`) and CLI automated test harness (`TestCLI`).
- Build and execution scripts: `test.sh` and `test.bat`.
- Project documentation governance: `VERSION.md`, `ROADMAP.md`, `TODO.md`, `VISION.md`, `MEMORY.md`, `DEPLOY.md`, `IDEAS.md`, `CHANGELOG.md`.
- Expanded test assertions in `TestRuntime` (228 assertions) covering chained pointer displacement, backward walking, boundary limits, and off-heap direct native memory slicing and memset.
- Expanded integration test suite (`IntegrationTest`): partial boundary accesses, slice upper boundary and negative offset protection, double-free rejection across heap and direct off-heap memory, quota limits, and pooling reuse sanitization.
- Expanded benchmark suite (`BenchmarkRuntime`): added C++ virtual method dispatch throughput benchmarking (CppObject/VTable), sprintf formatting throughput, and in-place qsort execution performance.

## [0.1.0] - 2026-06-25
### Added
- Initial implementation of `ManagedPointer`, `MemoryAllocator`, and `MemoryAccessException`.
- Null-terminated `readCString` and `writeCString`.
- `copyMemory` (memcpy) implementation.
- Basic test harness in `TestRuntime`.
