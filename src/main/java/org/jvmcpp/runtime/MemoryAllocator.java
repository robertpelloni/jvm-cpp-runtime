package org.jvmcpp.runtime;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicLong;

public final class MemoryAllocator {
    private final AtomicLong activeAllocations = new AtomicLong(0);
    private final AtomicLong totalAllocatedBytes = new AtomicLong(0);

    public MemoryAllocator() {
    }

    public long getActiveAllocationCount() {
        return activeAllocations.get();
    }

    public long getTotalAllocatedBytes() {
        return totalAllocatedBytes.get();
    }

    /**
     * Allocates on-heap memory of specified byte size.
     */
    public <T> ManagedPointer<T> malloc(long size) {
        if (size < 0 || size > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("Invalid allocation size: " + size);
        }

        byte[] memoryBlock = new byte[(int) size];
        ManagedPointer<T> pointer = new ManagedPointer<>(memoryBlock, 0, size);

        activeAllocations.incrementAndGet();
        totalAllocatedBytes.addAndGet(size);

        return pointer;
    }

    /**
     * Allocates zero-initialized memory for num elements of size bytes.
     */
    public <T> ManagedPointer<T> calloc(long num, long size) {
        if (num < 0 || size < 0) {
            throw new IllegalArgumentException("Negative dimensions for calloc");
        }
        long totalSize = num * size;
        ManagedPointer<T> ptr = malloc(totalSize);
        // Java arrays are already initialized to 0, but explicitly ensuring zero fill
        if (totalSize > 0) {
            ptr.fillMemory((byte) 0, totalSize);
        }
        return ptr;
    }

    /**
     * Frees and sanitizes the allocated pointer.
     */
    public void free(ManagedPointer<?> pointer) {
        if (pointer != null) {
            pointer.free();
            activeAllocations.decrementAndGet();
            totalAllocatedBytes.addAndGet(-pointer.getBounds());
        }
    }

    /**
     * Reallocates memory block to a new size. Copies existing data up to min(oldSize, newSize).
     * If pointer is null, behaves like malloc(newSize).
     * If newSize is 0, frees pointer and returns null.
     */
    public <T> ManagedPointer<T> realloc(ManagedPointer<T> pointer, long newSize) {
        if (pointer == null) {
            return malloc(newSize);
        }
        if (newSize == 0) {
            free(pointer);
            return null;
        }

        ManagedPointer<T> newPointer = malloc(newSize);
        long bytesToCopy = Math.min(pointer.getBounds(), newSize);
        if (bytesToCopy > 0) {
            copyMemory(newPointer, pointer, bytesToCopy);
        }
        // Free the old pointer and sanitize memory
        free(pointer);
        return newPointer;
    }

    /**
     * Sets a block of memory to the specified byte value (memset).
     */
    public static void fillMemory(ManagedPointer<?> ptr, byte value, long bytes) {
        if (ptr == null) {
            throw new IllegalArgumentException("Cannot fillMemory on null pointer");
        }
        ptr.fillMemory(value, bytes);
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
