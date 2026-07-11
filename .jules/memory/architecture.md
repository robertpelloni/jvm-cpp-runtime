### Architecture, Patterns, and Decisions

The **jvm-cpp-runtime** repository serves as the foundational Java runtime framework and target memory-abstraction layer for a custom C++ to JVM compiler. It aims to bridge the gap between low-level C++ memory concepts (pointers, manual allocation) and the managed, safety-oriented Java Virtual Machine.

#### Core Architecture & Components

1.  **`ManagedPointer<T>`**:
    *   **Purpose**: Acts as the Java equivalent of a C++ pointer. It encapsulates three fundamental fields to represent an address in memory:
        *   `base` (`Object`): Usually a primitive array (e.g., `byte[]`) acting as the underlying memory block.
        *   `offset` (`long`): The logical pointer's current offset from the base.
        *   `bounds` (`long`): The maximum size of the allocation, ensuring safe access.
    *   **Memory Access**: Relies heavily on `sun.misc.Unsafe` to perform raw read/write operations (e.g., `readByte()`, `writeInt()`, `readPointer()`). It uses `Unsafe.arrayBaseOffset(byte[].class)` to calculate precise physical addresses relative to the object reference.
    *   **Pointer Arithmetic**: Emulated immutably. Operations like `addOffset(long bytes)` return a new `ManagedPointer` instance reflecting the adjusted offset, allowing standard pointer traversal.

2.  **`MemoryAllocator`**:
    *   **Purpose**: Simulates the C++ heap (`malloc`).
    *   **Behavior**: Provisions standard `byte[]` arrays of requested lengths and wraps them in a `ManagedPointer`.
    *   **Memory Lifecycle**: Unlike C++ which requires an explicit `free()`, this architecture explicitly relies on the JVM Garbage Collector (GC). The `byte[]` allocation remains strongly referenced by the `ManagedPointer` wrapper. Once the "C++ application" (compiled to JVM bytecode) loses all references to the pointer, the JVM handles sweeping the memory.

3.  **`MemoryAccessException`**:
    *   **Purpose**: A custom `RuntimeException` providing deterministic failures.
    *   **Behavior**: Thrown by the `ManagedPointer` when bounds are violated (i.e., `offset < 0` or `offset + sizeof(type) > bounds`).

#### Key Patterns & Decisions

*   **Balancing "Unsafe" Speed with Java Safety**: A crucial architectural decision is combining the high performance and raw memory manipulation of `sun.misc.Unsafe` with strict bounds-checking before every memory access. This provides C++ styled semantics but prevents segmentation faults, substituting them with deterministic, debuggable Java exceptions (`MemoryAccessException`).
*   **GC-Driven Manual Memory Management**: By mapping manual allocations (`malloc`) to raw Java arrays and letting GC handle deallocation, the compiler avoids needing to implement a complex standalone garbage collector or memory pool for the C++ code, making the runtime significantly simpler.
*   **Primitive Foundation**: The runtime prefers operating on basic primitives (`byte`, `int`, `long`) and raw `byte[]` blocks, ensuring minimal object overhead during core memory manipulation, which is essential for low-level systems abstraction.