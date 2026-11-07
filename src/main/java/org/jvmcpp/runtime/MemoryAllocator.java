package org.jvmcpp.runtime;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicLong;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;

public final class MemoryAllocator {
    private final long maxHeapBytes;
    private final AtomicLong activeAllocations = new AtomicLong(0);
    private final AtomicLong totalAllocatedBytes = new AtomicLong(0);
    private final Map<Object, Long> allocatedBlockSizes = new ConcurrentHashMap<>();
    private final Map<Long, Long> directAllocations = new ConcurrentHashMap<>();

    // Fast memory pools for recycling common allocation sizes (e.g. 32, 64, 128, 256 bytes)
    private final Map<Long, ConcurrentLinkedQueue<ManagedPointer<?>>> freePools = new ConcurrentHashMap<>();

    public MemoryAllocator() {
        this(Long.MAX_VALUE);
    }

    public MemoryAllocator(long maxHeapBytes) {
        if (maxHeapBytes <= 0) {
            throw new IllegalArgumentException("maxHeapBytes must be positive: " + maxHeapBytes);
        }
        this.maxHeapBytes = maxHeapBytes;
    }

    public long getMaxHeapBytes() {
        return maxHeapBytes;
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

        if (totalAllocatedBytes.get() + size > maxHeapBytes) {
            throw new MemoryAccessException("Out of memory: allocation of " + size + " bytes exceeds heap limit of " + maxHeapBytes + " bytes (current: " + totalAllocatedBytes.get() + ")");
        }

        byte[] memoryBlock = new byte[(int) size];
        ManagedPointer<T> pointer = new ManagedPointer<>(memoryBlock, 0, size);

        allocatedBlockSizes.put(memoryBlock, size);
        activeAllocations.incrementAndGet();
        totalAllocatedBytes.addAndGet(size);

        return pointer;
    }

    /**
     * Allocates unmanaged off-heap direct native memory of specified byte size.
     */
    public <T> ManagedPointer<T> mallocDirect(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("Invalid allocation size: " + size);
        }

        if (totalAllocatedBytes.get() + size > maxHeapBytes) {
            throw new MemoryAccessException("Out of memory: direct allocation of " + size + " bytes exceeds limit of " + maxHeapBytes + " bytes (current: " + totalAllocatedBytes.get() + ")");
        }

        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);

            long rawAddress = unsafe.allocateMemory(size);
            ManagedPointer<T> pointer = new ManagedPointer<>(null, rawAddress, 0, size);

            directAllocations.put(rawAddress, size);
            activeAllocations.incrementAndGet();
            totalAllocatedBytes.addAndGet(size);

            return pointer;
        } catch (Exception e) {
            throw new RuntimeException("Failed to allocate off-heap direct memory", e);
        }
    }

    /**
     * Allocates a pooled memory block of specified size. If a free block of that size exists,
     * it is reused, avoiding GC churn and fragmentation.
     */
    @SuppressWarnings("unchecked")
    public <T> ManagedPointer<T> allocatePooled(long size) {
        ConcurrentLinkedQueue<ManagedPointer<?>> pool = freePools.get(size);
        if (pool != null) {
            ManagedPointer<?> reused = pool.poll();
            if (reused != null) {
                if (totalAllocatedBytes.get() + size > maxHeapBytes) {
                    // Re-add to pool and throw quota exception
                    pool.offer(reused);
                    throw new MemoryAccessException("Out of memory: pooled allocation of " + size + " bytes exceeds heap limit of " + maxHeapBytes + " bytes");
                }
                // Re-register block as active with exact size and offset reset to 0
                allocatedBlockSizes.put(reused.getBase(), size);
                activeAllocations.incrementAndGet();
                totalAllocatedBytes.addAndGet(size);
                return new ManagedPointer<>(reused.getBase(), 0, size);
            }
        }
        return malloc(size);
    }

    /**
     * Recycles a pointer into the free pool for its size, zeroing the contents and making it
     * immediately available for subsequent allocatePooled requests.
     */
    public void freePooled(ManagedPointer<?> pointer) {
        if (pointer == null) {
            return;
        }
        long size = pointer.getBounds();
        // Free and sanitize
        free(pointer);
        // Add to recycling pool
        freePools.computeIfAbsent(size, k -> new ConcurrentLinkedQueue<>()).offer(pointer);
    }

    /**
     * Allocates memory aligned to the specified alignment boundary (analogous to C11 aligned_alloc).
     *
     * @param alignment Must be a valid alignment (power of two).
     * @param size Size in bytes.
     */
    public <T> ManagedPointer<T> aligned_alloc(long alignment, long size) {
        if (alignment <= 0 || (alignment & (alignment - 1)) != 0) {
            throw new IllegalArgumentException("Alignment must be a positive power of two: " + alignment);
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be positive: " + size);
        }

        // Allocate extra space to allow aligning within the allocated block
        long totalAlloc = size + alignment;
        ManagedPointer<T> raw = malloc(totalAlloc);
        ManagedPointer<T> aligned = raw.align(alignment);
        // Slice to exact requested size
        return aligned.slice(0, size);
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
     * Throws MemoryAccessException if pointer was already freed or was not allocated by this allocator.
     */
    public void free(ManagedPointer<?> pointer) {
        if (pointer != null) {
            if (pointer.isDirect()) {
                long addr = pointer.getRawAddress();
                Long originalSize = directAllocations.remove(addr);
                if (originalSize == null) {
                    throw new MemoryAccessException("Double free or invalid direct pointer deallocation: " + addr);
                }
                pointer.free();
                activeAllocations.decrementAndGet();
                totalAllocatedBytes.addAndGet(-originalSize);
            } else {
                Object base = pointer.getBase();
                Long originalSize = base != null ? allocatedBlockSizes.remove(base) : null;
                if (originalSize == null) {
                    throw new MemoryAccessException("Double free or invalid pointer deallocation");
                }
                pointer.free();
                activeAllocations.decrementAndGet();
                totalAllocatedBytes.addAndGet(-originalSize);
            }
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
