# jvm-cpp-runtime
A brand new repository containing the core Java libraries that the compiled C++ code relies on for memory layouts and safety features.

Before touching the heavy-duty C++ compiler, you need the underlying Java classes that represent C++ concepts like pointers, pointer arithmetic, references, and standard memory blocks.

Prompt to feed your engineering LLM:
Plaintext
Act as a Senior Java Architect working on low-level systems abstractions. Write a high-performance Java runtime framework that acts as the target layer for a C++ to JVM compiler. 

Implement the following classes within a package named `org.jvmcpp.runtime`:
1. `public final class ManagedPointer<T>`: It must encapsulate three fields: `private final Object base;`, `private long offset;`, and `private final long bounds;`. Provide methods for:
   - `readByte()`, `readInt()`, `readLong()`, `readPointer()` that use Unsafe or MemorySegment mechanics to safely fetch values relative to `base + offset`.
   - Before every read/write operation, perform an explicit bounds-check: if `offset + sizeof(type) > bounds` or `offset < 0`, throw an explicit runtime `MemoryAccessException`.
   - Implement pointer arithmetic: a method `addOffset(long bytes)` that returns a new `ManagedPointer` instance tracking the adjusted offset.
2. `public final class MemoryAllocator`: Implement a managed on-heap heap layer. Provi
