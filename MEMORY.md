# MEMORY

## Architectural Observations
- **Memory Abstraction Model**: Pointers wrap a `base` object (default `byte[]`), a current `offset` (long), and a maximum `bounds` limit (long).
- **Direct Memory Access**: Uses `sun.misc.Unsafe` with array offset calculation for direct reads/writes.
- **Safety Guarantee**: Every accessor validates offsets against bounds before interacting with `Unsafe`. Out-of-bounds accesses reliably throw `MemoryAccessException`.
- **GC Integration**: Allocations leverage JVM managed byte arrays so references held by `ManagedPointer` retain memory without requiring manual JVM heap pinning, while `free()` explicitly zeroes memory blocks for sanitization.
