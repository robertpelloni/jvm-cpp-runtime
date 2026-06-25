package org.jvmcpp.runtime;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MemoryAllocator {
    // Keep strong references to allocated memory blocks to prevent premature GC
    // This simulates manual memory management where C++ code explicitly frees memory
    // Alternatively, this allows the JVM to GC it when the C++ application loses all references
    // to the ManagedPointer wrapper, by not strictly pinning it here indefinitely.
    // However, the prompt says: "keeps track of references to ensure the Java GC doesn't prematurely sweep them unless the C++ application loses all references to the pointer."
    // Actually, if we just return a ManagedPointer that holds a strong reference to the byte[],
    // the JVM's GC already guarantees it won't be swept until the ManagedPointer is unreachable.
    // Let's hold a weak set of allocations or a set of objects to match the prompt's intent.
    // The prompt says: "keeps track of references to ensure the Java GC doesn't prematurely sweep them unless the C++ application loses all references to the pointer."
    // If we just return the ManagedPointer, the ManagedPointer holds a strong reference to `base` (the byte[]).
    // The Java GC inherently keeps the byte[] alive as long as the ManagedPointer is reachable.

    public MemoryAllocator() {
    }

    public <T> ManagedPointer<T> malloc(long size) {
        if (size < 0 || size > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("Invalid allocation size: " + size);
        }

        // Provision primitive byte[] array
        byte[] memoryBlock = new byte[(int) size];

        // Construct a ManagedPointer wrapping that array
        ManagedPointer<T> pointer = new ManagedPointer<>(memoryBlock, 0, size);

        // The JVM garbage collector natively "keeps track of references to ensure the Java GC doesn't
        // prematurely sweep them unless the C++ application loses all references to the pointer."
        // Because `pointer` holds a strong reference to `memoryBlock` in its `base` field.

        return pointer;
    }

    public void free(ManagedPointer<?> pointer) {
        if (pointer != null) {
            pointer.free();
        }
    }

    public static void copyMemory(ManagedPointer<?> dest, ManagedPointer<?> src, long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Cannot copy negative bytes: " + bytes);
        }
        if (bytes == 0) {
            return;
        }

        // Validate bounds on both pointers before performing Unsafe operations
        if (dest.getOffset() < 0 || dest.getOffset() + bytes > dest.getBounds()) {
            throw new MemoryAccessException("Destination memory access out of bounds for copyMemory. Offset: " + dest.getOffset() + ", size: " + bytes + ", bounds: " + dest.getBounds());
        }
        if (src.getOffset() < 0 || src.getOffset() + bytes > src.getBounds()) {
            throw new MemoryAccessException("Source memory access out of bounds for copyMemory. Offset: " + src.getOffset() + ", size: " + bytes + ", bounds: " + src.getBounds());
        }

        // Reflection hack to get Unsafe inside MemoryAllocator without exporting from ManagedPointer
        // OR better yet, let's just make sure both pointers can resolve addresses. We cannot access resolveAddress easily.
        // It's cleaner to implement a package-private blockCopy in ManagedPointer or let MemoryAllocator rely on looping primitive reads/writes if it doesn't have direct Unsafe access.
        // Let's implement blockCopy by fetching Unsafe directly here just like ManagedPointer.
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);

            long byteArrayOffset = unsafe.arrayBaseOffset(byte[].class);

            long destAddress = dest.getBase() instanceof byte[] ? byteArrayOffset + dest.getOffset() : dest.getOffset();
            long srcAddress = src.getBase() instanceof byte[] ? byteArrayOffset + src.getOffset() : src.getOffset();

            unsafe.copyMemory(src.getBase(), srcAddress, dest.getBase(), destAddress, bytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute copyMemory", e);
        }
    }
}
