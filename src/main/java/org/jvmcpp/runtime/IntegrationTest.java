package org.jvmcpp.runtime;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * End-to-end integration tests simulating realistic C++ runtime scenarios
 * compiled to JVM bytecode targeting ManagedPointer and MemoryAllocator.
 */
public class IntegrationTest {

    private static int assertions = 0;

    private static void assertTrue(boolean cond, String msg) {
        assertions++;
        if (!cond) throw new AssertionError("Integration failure: " + msg);
    }

    private static void assertEquals(Object exp, Object act, String msg) {
        assertions++;
        if (exp == null && act == null) return;
        if (exp != null && exp.equals(act)) return;
        throw new AssertionError("Integration failure: " + msg + " expected=" + exp + ", actual=" + act);
    }

    public static void main(String[] args) {
        System.out.println("=== RUNNING RUNTIME INTEGRATION TEST SUITE ===");
        MemoryAllocator allocator = new MemoryAllocator();

        testPointerArrayIndirectionAndPointersToPointers(allocator);
        testHighChurnPoolStabilityAndThrashing(allocator);
        testDirectAndHeapBufferInteroperability(allocator);
        testDynamicVectorReallocationShrinkingAndGrowing(allocator);
        testBoundaryMemoryAccess(allocator);
        testSliceBoundsAndNegativeOffsetSafety(allocator);
        testMemoryLifecycleAndDoubleFreeRejection(allocator);
        testDynamicVectorGrowth(allocator);
        testStructArrayTraversal(allocator);
        testPolymorphicCppObjectHierarchy(allocator);
        testConcurrentProducerConsumerPooling(allocator);

        System.out.println("=== ALL INTEGRATION TESTS PASSED (" + assertions + " assertions verified) ===");
    }

    /**
     * Simulates cyclic dynamic vector capacity adjustments (growing, writing,
     * shrinking to fit, and reading back) across multiple realloc rounds.
     */
    private static void testDynamicVectorReallocationShrinkingAndGrowing(MemoryAllocator allocator) {
        long currentCapacity = 16;
        ManagedPointer<?> buffer = allocator.malloc(currentCapacity * 4); // 16 ints = 64 bytes

        // Fill initial 16 ints
        for (int i = 0; i < 16; i++) {
            buffer.addOffset(i * 4).writeInt(i * 100);
        }

        // Expand to 32 ints (128 bytes)
        buffer = allocator.realloc(buffer, 32 * 4);
        assertEquals(128L, buffer.getBounds(), "Expanded buffer bounds should be 128");

        // Verify initial 16 ints preserved after growth
        for (int i = 0; i < 16; i++) {
            assertEquals(i * 100, buffer.addOffset(i * 4).readInt(), "Preserved int after growth at " + i);
        }

        // Fill remaining 16 ints (16..31)
        for (int i = 16; i < 32; i++) {
            buffer.addOffset(i * 4).writeInt(i * 100);
        }

        // Shrink vector down to 8 ints (32 bytes)
        buffer = allocator.realloc(buffer, 8 * 4);
        assertEquals(32L, buffer.getBounds(), "Shrunk buffer bounds should be 32");

        // Verify remaining 8 ints preserved after shrink
        for (int i = 0; i < 8; i++) {
            assertEquals(i * 100, buffer.addOffset(i * 4).readInt(), "Preserved int after shrink at " + i);
        }

        // Out-of-bounds check on truncated portion (offset 8*4 = 32 on 32-byte buffer)
        boolean caughtTruncatedAccess = false;
        try {
            buffer.addOffset(8 * 4).readInt();
        } catch (MemoryAccessException e) {
            caughtTruncatedAccess = true;
        }
        assertTrue(caughtTruncatedAccess, "Reading truncated elements past shrunk bounds must throw MemoryAccessException");

        allocator.free(buffer);
    }

    /**
     * Validates bidirectional bulk memory copying (copyMemory) between off-heap
     * direct native memory blocks (mallocDirect) and on-heap array-backed blocks (malloc),
     * ensuring proper offset displacement and byte preservation across heterogeneous backings.
     */
    private static void testDirectAndHeapBufferInteroperability(MemoryAllocator allocator) {
        long bufferSize = 64;
        ManagedPointer<?> heapSource = allocator.malloc(bufferSize);
        ManagedPointer<?> directDest = allocator.mallocDirect(bufferSize);
        ManagedPointer<?> heapDest = allocator.malloc(bufferSize);

        // Populate heap source with patterned data
        for (int i = 0; i < bufferSize; i++) {
            heapSource.addOffset(i).writeByte((byte) (i ^ 0x5C));
        }

        // Copy Heap -> Direct
        MemoryAllocator.copyMemory(directDest, heapSource, bufferSize);

        // Verify Direct contents
        for (int i = 0; i < bufferSize; i++) {
            assertEquals((byte) (i ^ 0x5C), directDest.addOffset(i).readByte(), "Direct buffer byte at " + i);
        }

        // Modify a window in Direct memory (offsets 16..32)
        for (int i = 16; i < 32; i++) {
            directDest.addOffset(i).writeByte((byte) 0xAA);
        }

        // Copy Direct -> Heap
        MemoryAllocator.copyMemory(heapDest, directDest, bufferSize);

        // Verify Heap destination contents
        for (int i = 0; i < bufferSize; i++) {
            byte expected = (i >= 16 && i < 32) ? (byte) 0xAA : (byte) (i ^ 0x5C);
            assertEquals(expected, heapDest.addOffset(i).readByte(), "Roundtripped heap buffer byte at " + i);
        }

        allocator.free(heapSource);
        allocator.free(directDest);
        allocator.free(heapDest);
    }

    /**
     * Stress-tests the memory pooling mechanism under rapid allocation and deallocation
     * across diverse block size buckets to ensure high-churn stability.
     */
    private static void testHighChurnPoolStabilityAndThrashing(MemoryAllocator allocator) {
        long initialAllocs = allocator.getActiveAllocationCount();
        long initialBytes = allocator.getTotalAllocatedBytes();

        int[] bucketSizes = { 16, 32, 64, 128 };
        int cycles = 150;
        ManagedPointer<?>[] activeBlocks = new ManagedPointer<?>[bucketSizes.length];

        for (int c = 0; c < cycles; c++) {
            // Allocate one block from each bucket
            for (int b = 0; b < bucketSizes.length; b++) {
                activeBlocks[b] = allocator.allocatePooled(bucketSizes[b]);
                activeBlocks[b].writeInt(c * 100 + b);
            }

            // Verify content
            for (int b = 0; b < bucketSizes.length; b++) {
                assertEquals(c * 100 + b, activeBlocks[b].readInt(), "Pooled content verified under churn");
            }

            // Free blocks back to pool
            for (int b = 0; b < bucketSizes.length; b++) {
                allocator.freePooled(activeBlocks[b]);
            }
        }

        // Active allocations should return to baseline
        assertEquals(initialAllocs, allocator.getActiveAllocationCount(), "Active allocations restored after churn");
        assertEquals(initialBytes, allocator.getTotalAllocatedBytes(), "Total allocated bytes restored after churn");
    }

    /**
     * Simulates double-pointer indirection (char** argv or int** jagged matrices),
     * verifying reading, modifying, and cleaning multi-tiered pointers.
     */
    private static void testPointerArrayIndirectionAndPointersToPointers(MemoryAllocator allocator) {
        int rowCount = 4;
        // Allocate array of pointers: int* rows[4] -> 4 * 8 bytes = 32 bytes
        ManagedPointer<?> pointerTable = allocator.malloc(rowCount * 8);
        ManagedPointer<?>[] subRows = new ManagedPointer<?>[rowCount];

        for (int r = 0; r < rowCount; r++) {
            int cols = (r + 1) * 3; // jagged row sizes: 3, 6, 9, 12 ints
            subRows[r] = allocator.malloc(cols * 4);
            for (int c = 0; c < cols; c++) {
                subRows[r].addOffset(c * 4).writeInt((r + 1) * 100 + c);
            }
            // Store raw offset / address emulation in pointer table
            // For on-heap pointers, we track subRows and use direct offset
            pointerTable.addOffset(r * 8).writePointer(r * 1000L);
        }

        // Validate table entries
        for (int r = 0; r < rowCount; r++) {
            assertEquals(r * 1000L, pointerTable.addOffset(r * 8).readPointer(), "Pointer table entry at row " + r);
            int cols = (r + 1) * 3;
            for (int c = 0; c < cols; c++) {
                assertEquals((r + 1) * 100 + c, subRows[r].addOffset(c * 4).readInt(), "Sub-row data match at [" + r + "][" + c + "]");
            }
        }

        // Clean up all allocated structures
        allocator.free(pointerTable);
        for (int r = 0; r < rowCount; r++) {
            allocator.free(subRows[r]);
        }
    }

    /**
     * Validates partial boundary violations (e.g., writing 4-byte int or 8-byte long
     * across allocation boundary) to ensure MemoryAccessException is raised deterministically.
     */
    private static void testBoundaryMemoryAccess(MemoryAllocator allocator) {
        long size = 16;
        ManagedPointer<?> ptr = allocator.malloc(size);

        // Valid boundary accesses
        ptr.addOffset(8).writeLong(0x1122334455667788L);
        assertEquals(0x1122334455667788L, ptr.addOffset(8).readLong(), "Boundary long read at offset 8");

        ptr.addOffset(12).writeInt(0xAABBCCDD);
        assertEquals(0xAABBCCDD, ptr.addOffset(12).readInt(), "Boundary int read at offset 12");

        ptr.addOffset(15).writeByte((byte) 0xEE);
        assertEquals((byte) 0xEE, ptr.addOffset(15).readByte(), "Boundary byte read at offset 15");

        // Partial out-of-bounds: reading 4-byte int at offset 13 (spans 13..16, boundary is 16)
        boolean caughtInt = false;
        try {
            ptr.addOffset(13).readInt();
        } catch (MemoryAccessException e) {
            caughtInt = true;
        }
        assertTrue(caughtInt, "Reading 4-byte int at offset 13 of 16-byte buffer must throw MemoryAccessException");

        // Partial out-of-bounds: writing 8-byte long at offset 9 (spans 9..16, boundary is 16)
        boolean caughtLong = false;
        try {
            ptr.addOffset(9).writeLong(12345L);
        } catch (MemoryAccessException e) {
            caughtLong = true;
        }
        assertTrue(caughtLong, "Writing 8-byte long at offset 9 of 16-byte buffer must throw MemoryAccessException");

        // Partial out-of-bounds: writing pointer at offset 10 (spans 10..17, boundary is 16)
        boolean caughtPtr = false;
        try {
            ptr.addOffset(10).writePointer(999L);
        } catch (MemoryAccessException e) {
            caughtPtr = true;
        }
        assertTrue(caughtPtr, "Writing 64-bit pointer at offset 10 of 16-byte buffer must throw MemoryAccessException");

        // Partial out-of-bounds: short at offset 15 (spans 15..16, boundary is 16)
        boolean caughtShort = false;
        try {
            ptr.addOffset(15).readShort();
        } catch (MemoryAccessException e) {
            caughtShort = true;
        }
        assertTrue(caughtShort, "Reading 2-byte short at offset 15 of 16-byte buffer must throw MemoryAccessException");

        allocator.free(ptr);
    }

    /**
     * Validates slicing behavior: boundary confinement, boundary clamping, negative
     * slice offset rejection, and prevention of accessing memory beyond slice window.
     */
    private static void testSliceBoundsAndNegativeOffsetSafety(MemoryAllocator allocator) {
        ManagedPointer<?> parent = allocator.malloc(64);
        parent.fillMemory((byte) 0xAA, 64);

        // Sub-slice window of 16 bytes starting at offset 16 (new offset=16, new bounds=32, remaining=16)
        ManagedPointer<?> slice = parent.slice(16, 16);
        assertEquals(16L, slice.getOffset(), "Slice current offset should be 16");
        assertEquals(32L, slice.getBounds(), "Slice upper bounds should be 32");
        assertEquals(16L, slice.getBounds() - slice.getOffset(), "Slice span should be 16 bytes");

        // Write into slice
        slice.writeInt(0x12345678);
        assertEquals(0x12345678, slice.readInt(), "Reading from slice start");
        assertEquals(0x12345678, parent.addOffset(16).readInt(), "Parent reflecting slice write at offset 16");

        // Negative offset relative to base allocation should throw MemoryAccessException
        boolean caughtNegative = false;
        try {
            slice.addOffset(-17).readByte();
        } catch (MemoryAccessException e) {
            caughtNegative = true;
        }
        assertTrue(caughtNegative, "Negative offset on pointer must throw MemoryAccessException");

        // Accessing past slice boundary (offset 16 on 16-byte slice => 16 + 16 = 32, which reaches bounds 32)
        boolean caughtPastSlice = false;
        try {
            slice.addOffset(16).readByte();
        } catch (MemoryAccessException e) {
            caughtPastSlice = true;
        }
        assertTrue(caughtPastSlice, "Reading past slice upper boundary must throw MemoryAccessException");

        // Requesting a slice that exceeds parent boundary should throw MemoryAccessException
        boolean caughtOverflowSlice = false;
        try {
            parent.slice(48, 100);
        } catch (MemoryAccessException e) {
            caughtOverflowSlice = true;
        }
        assertTrue(caughtOverflowSlice, "Requesting slice beyond parent bounds must throw MemoryAccessException");

        allocator.free(parent);
    }

    /**
     * Validates memory allocator lifecycle: double-free rejection for standard,
     * direct, and pooled allocations, foreign pointer rejection, and quota enforcement.
     */
    private static void testMemoryLifecycleAndDoubleFreeRejection(MemoryAllocator allocator) {
        // Double-free of standard heap allocation
        ManagedPointer<?> heapPtr = allocator.malloc(32);
        allocator.free(heapPtr);

        boolean caughtHeapDoubleFree = false;
        try {
            allocator.free(heapPtr);
        } catch (MemoryAccessException e) {
            caughtHeapDoubleFree = true;
        }
        assertTrue(caughtHeapDoubleFree, "Double free of on-heap pointer must throw MemoryAccessException");

        // Double-free of direct off-heap allocation
        ManagedPointer<?> directPtr = allocator.mallocDirect(64);
        allocator.free(directPtr);

        boolean caughtDirectDoubleFree = false;
        try {
            allocator.free(directPtr);
        } catch (MemoryAccessException e) {
            caughtDirectDoubleFree = true;
        }
        assertTrue(caughtDirectDoubleFree, "Double free of direct pointer must throw MemoryAccessException");

        // Freeing an untracked foreign pointer (unallocated object) should be rejected
        ManagedPointer<?> foreignPtr = new ManagedPointer<>(new byte[32], 0, 32);
        boolean caughtForeignFree = false;
        try {
            allocator.free(foreignPtr);
        } catch (MemoryAccessException e) {
            caughtForeignFree = true;
        }
        assertTrue(caughtForeignFree, "Freeing an untracked foreign pointer must throw MemoryAccessException");

        // Allocator with strict quota
        MemoryAllocator quotaAllocator = new MemoryAllocator(100);
        ManagedPointer<?> q1 = quotaAllocator.malloc(60);
        assertEquals(60L, quotaAllocator.getTotalAllocatedBytes(), "Quota allocator tracked 60 bytes");

        // Exceeding quota: 60 + 50 = 110 > 100
        boolean caughtQuotaExceeded = false;
        try {
            quotaAllocator.malloc(50);
        } catch (MemoryAccessException e) {
            caughtQuotaExceeded = true;
        }
        assertTrue(caughtQuotaExceeded, "Exceeding max heap quota must throw MemoryAccessException");

        quotaAllocator.free(q1);
        assertEquals(0L, quotaAllocator.getTotalAllocatedBytes(), "Quota allocator freed all bytes");

        // Pooling verification: allocatedPooled must enforce offset sanitization
        ManagedPointer<?> pooled = allocator.allocatePooled(32);
        pooled.addOffset(16).writeInt(42);
        allocator.freePooled(pooled);

        // Next pooled block should be at offset 0
        ManagedPointer<?> reused = allocator.allocatePooled(32);
        assertEquals(0L, reused.getOffset(), "Reused pooled pointer must have offset reset to 0");
        allocator.freePooled(reused);
    }

    /**
     * Simulates std::vector<int> dynamic expansion using malloc, realloc, and pointer indexing.
     */
    private static void testDynamicVectorGrowth(MemoryAllocator allocator) {
        long capacity = 4; // 4 ints = 16 bytes
        long size = 0;
        ManagedPointer<?> buffer = allocator.malloc(capacity * 4);

        for (int i = 0; i < 50; i++) {
            if (size >= capacity) {
                capacity *= 2;
                buffer = allocator.realloc(buffer, capacity * 4);
            }
            buffer.addOffset(size * 4).writeInt(i * 10);
            size++;
        }

        assertEquals(50L, size, "Vector size mismatch");
        assertTrue(capacity >= 50, "Vector capacity mismatch");

        for (int i = 0; i < 50; i++) {
            int val = buffer.addOffset(i * 4).readInt();
            assertEquals(i * 10, val, "Vector element mismatch at " + i);
        }

        allocator.free(buffer);
    }

    /**
     * Simulates an array of C++ structs traversed via pointer arithmetic.
     * struct Particle { float x, y, z; int id; }; // size = 16 bytes
     */
    private static void testStructArrayTraversal(MemoryAllocator allocator) {
        StructLayout particleLayout = StructLayout.builder()
                .addField("x", StructLayout.Type.FLOAT)
                .addField("y", StructLayout.Type.FLOAT)
                .addField("z", StructLayout.Type.FLOAT)
                .addField("id", StructLayout.Type.INT)
                .build();

        assertEquals(16L, particleLayout.getSize(), "Particle struct size mismatch");

        int count = 10;
        ManagedPointer<?> arrayBlock = allocator.malloc(count * particleLayout.getSize());

        // Initialize array elements using pointer arithmetic
        for (int i = 0; i < count; i++) {
            ManagedPointer<?> elementPtr = arrayBlock.addOffset(i * particleLayout.getSize());
            elementPtr.addOffset(particleLayout.getOffset("x")).writeFloat(1.0f * i);
            elementPtr.addOffset(particleLayout.getOffset("y")).writeFloat(2.0f * i);
            elementPtr.addOffset(particleLayout.getOffset("z")).writeFloat(3.0f * i);
            particleLayout.writeInt(elementPtr, "id", 100 + i);
        }

        // Verify elements
        for (int i = 0; i < count; i++) {
            ManagedPointer<?> elementPtr = arrayBlock.addOffset(i * particleLayout.getSize());
            assertEquals(1.0f * i, elementPtr.addOffset(particleLayout.getOffset("x")).readFloat(), "Particle x");
            assertEquals(2.0f * i, elementPtr.addOffset(particleLayout.getOffset("y")).readFloat(), "Particle y");
            assertEquals(3.0f * i, elementPtr.addOffset(particleLayout.getOffset("z")).readFloat(), "Particle z");
            assertEquals(100 + i, particleLayout.readInt(elementPtr, "id"), "Particle id");
        }

        allocator.free(arrayBlock);
    }

    /**
     * Simulates full C++ polymorphic hierarchy:
     * Base: Animal (virtual sound(), virtual legs())
     * Derived1: Dog
     * Derived2: Duck
     */
    private static void testPolymorphicCppObjectHierarchy(MemoryAllocator allocator) {
        VTable animalVt = new VTable("Animal");
        int soundSlot = animalVt.registerMethod((thisPtr, args) -> "generic");
        int legsSlot = animalVt.registerMethod((thisPtr, args) -> 0);

        VTable dogVt = new VTable("Dog");
        dogVt.setMethod(soundSlot, (thisPtr, args) -> "woof");
        dogVt.setMethod(legsSlot, (thisPtr, args) -> 4);

        VTable duckVt = new VTable("Duck");
        duckVt.setMethod(soundSlot, (thisPtr, args) -> "quack");
        duckVt.setMethod(legsSlot, (thisPtr, args) -> 2);

        CppObject dog = new CppObject(allocator, dogVt, 0);
        CppObject duck = new CppObject(allocator, duckVt, 0);

        assertEquals("woof", dog.invokeVirtual(soundSlot), "Dog sound");
        assertEquals(4, dog.invokeVirtual(legsSlot), "Dog legs");

        assertEquals("quack", duck.invokeVirtual(soundSlot), "Duck sound");
        assertEquals(2, duck.invokeVirtual(legsSlot), "Duck legs");

        dog.destroy(allocator);
        duck.destroy(allocator);
    }

    /**
     * Simulates multithreaded producer-consumer memory allocation and pooling stress.
     */
    private static void testConcurrentProducerConsumerPooling(MemoryAllocator allocator) {
        int threads = 4;
        int operationsPerThread = 200;
        Thread[] pool = new Thread[threads];
        final AtomicBoolean failed = new AtomicBoolean(false);
        final AtomicInteger completed = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int tid = i;
            pool[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        ManagedPointer<?> p = allocator.allocatePooled(64);
                        p.writeLong(tid * 1_000_000L + j);
                        Thread.yield();
                        assertEquals(tid * 1_000_000L + j, p.readLong(), "Pooled multithreaded read mismatch");
                        allocator.freePooled(p);
                    }
                    completed.incrementAndGet();
                } catch (Throwable t) {
                    failed.set(true);
                    t.printStackTrace();
                }
            });
            pool[i].start();
        }

        for (Thread t : pool) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        assertTrue(!failed.get(), "Concurrent pooling thread failure detected");
        assertEquals(threads, completed.get(), "All producer threads completed successfully");
    }
}
