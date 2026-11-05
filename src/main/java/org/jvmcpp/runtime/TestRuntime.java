package org.jvmcpp.runtime;

public class TestRuntime {
    public static void main(String[] args) {
        MemoryAllocator allocator = new MemoryAllocator();
        ManagedPointer<?> ptr = allocator.malloc(64); // Allocate 64 bytes

        // Test writing and reading byte
        ptr.writeByte((byte) 42);
        if (ptr.readByte() != 42) throw new AssertionError("Byte mismatch");

        // Test pointer arithmetic and bounds
        ManagedPointer<?> ptr2 = ptr.addOffset(4);
        ptr2.writeInt(123456789);
        if (ptr2.readInt() != 123456789) throw new AssertionError("Int mismatch");

        // Bounds exception
        try {
            ManagedPointer<?> outOfBounds = ptr.addOffset(62);
            outOfBounds.readInt(); // 62 + 4 = 66 > 64 -> Should throw
            throw new AssertionError("Did not throw bounds exception");
        } catch (MemoryAccessException e) {
            System.out.println("Caught expected exception: " + e.getMessage());
        }

        // Test pointer (long)
        ManagedPointer<?> ptr3 = ptr.addOffset(8);
        ptr3.writePointer(0xDEADBEEFCAFEBABEL);
        if (ptr3.readPointer() != 0xDEADBEEFCAFEBABEL) throw new AssertionError("Pointer mismatch");

        // Test short, float, double
        ManagedPointer<?> ptr4 = ptr.addOffset(16);
        ptr4.writeShort((short) 1234);
        if (ptr4.readShort() != 1234) throw new AssertionError("Short mismatch");

        ManagedPointer<?> ptr5 = ptr.addOffset(20);
        ptr5.writeFloat(3.14f);
        if (ptr5.readFloat() != 3.14f) throw new AssertionError("Float mismatch");

        ManagedPointer<?> ptr6 = ptr.addOffset(24);
        ptr6.writeDouble(2.71828);
        if (ptr6.readDouble() != 2.71828) throw new AssertionError("Double mismatch");

        // Test negative offset
        ManagedPointer<?> ptr7 = ptr6.addOffset(-4);
        if (ptr7.readFloat() != 3.14f) throw new AssertionError("Negative offset mismatch");

        // Test CString operations
        ManagedPointer<?> strPtr = allocator.malloc(64);
        strPtr.writeCString("Hello World");
        if (!strPtr.readCString().equals("Hello World")) throw new AssertionError("CString read/write mismatch");

        // Test copyMemory (memcpy)
        ManagedPointer<?> srcPtr = allocator.malloc(32);
        srcPtr.writeCString("Copy Me");

        ManagedPointer<?> destPtr = allocator.malloc(32);
        MemoryAllocator.copyMemory(destPtr, srcPtr, 8); // "Copy Me\0" = 8 bytes

        if (!destPtr.readCString().equals("Copy Me")) throw new AssertionError("copyMemory (memcpy) failed");

        // Test free
        allocator.free(ptr);
        if (ptr.readByte() != 0) throw new AssertionError("Free did not clear memory");

        // Test boolean
        ManagedPointer<?> boolPtr = allocator.malloc(4);
        boolPtr.writeBoolean(true);
        if (!boolPtr.readBoolean()) throw new AssertionError("Boolean true mismatch");
        boolPtr.writeBoolean(false);
        if (boolPtr.readBoolean()) throw new AssertionError("Boolean false mismatch");

        // Test char
        ManagedPointer<?> charPtr = allocator.malloc(4);
        charPtr.writeChar('Z');
        if (charPtr.readChar() != 'Z') throw new AssertionError("Char mismatch");

        // Test fillMemory / memset
        ManagedPointer<?> fillPtr = allocator.malloc(16);
        fillPtr.fillMemory((byte) 0xAA, 16);
        for (int i = 0; i < 16; i++) {
            if (fillPtr.addOffset(i).readByte() != (byte) 0xAA) {
                throw new AssertionError("fillMemory failed at index " + i);
            }
        }
        MemoryAllocator.fillMemory(fillPtr, (byte) 0x55, 8);
        for (int i = 0; i < 8; i++) {
            if (fillPtr.addOffset(i).readByte() != (byte) 0x55) {
                throw new AssertionError("MemoryAllocator.fillMemory failed at index " + i);
            }
        }
        if (fillPtr.addOffset(8).readByte() != (byte) 0xAA) {
            throw new AssertionError("fillMemory boundary check failed");
        }

        // Test realloc - grow
        ManagedPointer<?> reallocPtr = allocator.malloc(8);
        reallocPtr.writeInt(4242);
        ManagedPointer<?> grownPtr = allocator.realloc(reallocPtr, 32);
        if (grownPtr.getBounds() != 32) throw new AssertionError("realloc bounds mismatch");
        if (grownPtr.readInt() != 4242) throw new AssertionError("realloc data copy mismatch");

        // Test realloc - shrink
        ManagedPointer<?> shrunkPtr = allocator.realloc(grownPtr, 4);
        if (shrunkPtr.getBounds() != 4) throw new AssertionError("realloc shrink bounds mismatch");
        if (shrunkPtr.readInt() != 4242) throw new AssertionError("realloc shrink data mismatch");

        // Test realloc with null
        ManagedPointer<?> nullRealloc = allocator.realloc(null, 16);
        if (nullRealloc == null || nullRealloc.getBounds() != 16) throw new AssertionError("realloc(null, size) failed");

        // Test realloc with 0
        ManagedPointer<?> freedRealloc = allocator.realloc(nullRealloc, 0);
        if (freedRealloc != null) throw new AssertionError("realloc(ptr, 0) should return null");

        // Test calloc
        ManagedPointer<?> callocPtr = allocator.calloc(4, 8); // 32 bytes
        if (callocPtr.getBounds() != 32) throw new AssertionError("calloc bounds mismatch");
        for (int i = 0; i < 32; i++) {
            if (callocPtr.addOffset(i).readByte() != 0) {
                throw new AssertionError("calloc not zeroed at offset " + i);
            }
        }

        // Test allocator metrics tracking
        allocator.free(callocPtr);
        allocator.free(shrunkPtr);
        allocator.free(fillPtr);
        allocator.free(charPtr);
        allocator.free(boolPtr);
        allocator.free(destPtr);
        allocator.free(srcPtr);
        allocator.free(strPtr);

        if (allocator.getActiveAllocationCount() != 0) {
            throw new AssertionError("Active allocation count mismatch: " + allocator.getActiveAllocationCount());
        }
        if (allocator.getTotalAllocatedBytes() != 0) {
            throw new AssertionError("Total allocated bytes mismatch: " + allocator.getTotalAllocatedBytes());
        }

        // Test double free prevention
        try {
            allocator.free(callocPtr);
            throw new AssertionError("Did not detect double free");
        } catch (MemoryAccessException e) {
            System.out.println("Caught expected double-free exception: " + e.getMessage());
        }

        // Test heap quota limit (bounded allocator)
        MemoryAllocator boundedAllocator = new MemoryAllocator(128); // 128 bytes max
        ManagedPointer<?> p1 = boundedAllocator.malloc(100);
        try {
            boundedAllocator.malloc(50); // 100 + 50 = 150 > 128 -> should fail
            throw new AssertionError("Did not enforce heap limit");
        } catch (MemoryAccessException e) {
            System.out.println("Caught expected heap quota exception: " + e.getMessage());
        }
        boundedAllocator.free(p1);

        // Test pointer arithmetic: diff, compareTo, slice, equals
        ManagedPointer<?> block = allocator.malloc(64);
        ManagedPointer<?> pStart = block.addOffset(8);
        ManagedPointer<?> pEnd = block.addOffset(24);

        if (pEnd.diff(pStart) != 16) throw new AssertionError("Pointer diff mismatch");
        if (pStart.diff(pEnd) != -16) throw new AssertionError("Pointer reverse diff mismatch");

        // CompareTo
        if (((ManagedPointer) pStart).compareTo(pEnd) >= 0) throw new AssertionError("pStart should be < pEnd");
        if (((ManagedPointer) pEnd).compareTo(pStart) <= 0) throw new AssertionError("pEnd should be > pStart");
        if (((ManagedPointer) pStart).compareTo(pStart) != 0) throw new AssertionError("pStart compareTo pStart should be 0");

        // Equals
        ManagedPointer<?> pStartCopy = block.addOffset(8);
        if (!pStart.equals(pStartCopy)) throw new AssertionError("pStart should equal pStartCopy");

        // Slice
        ManagedPointer<?> slice = block.slice(10, 20);
        if (slice.getOffset() != 10) throw new AssertionError("Slice offset mismatch");
        if (slice.getBounds() != 30) throw new AssertionError("Slice bounds mismatch");

        // Free block
        allocator.free(block);

        // Test ByteBuffer and toByteArray
        ManagedPointer<?> byteBufBlock = allocator.malloc(16);
        byteBufBlock.writeInt(0x12345678);
        java.nio.ByteBuffer bb = byteBufBlock.asByteBuffer();
        bb.order(java.nio.ByteOrder.nativeOrder());
        if (bb.getInt(0) != 0x12345678) throw new AssertionError("ByteBuffer getInt mismatch");

        byte[] exportedBytes = byteBufBlock.toByteArray();
        if (exportedBytes.length != 16) throw new AssertionError("toByteArray length mismatch");
        allocator.free(byteBufBlock);

        // Multi-threaded stress test
        int threadsCount = 4;
        Thread[] threads = new Thread[threadsCount];
        final java.util.concurrent.atomic.AtomicBoolean threadFailed = new java.util.concurrent.atomic.AtomicBoolean(false);

        for (int i = 0; i < threadsCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int iter = 0; iter < 100; iter++) {
                        ManagedPointer<?> p = allocator.malloc(32);
                        p.writeLong(threadId * 1000L + iter);
                        if (p.readLong() != threadId * 1000L + iter) {
                            threadFailed.set(true);
                        }
                        allocator.free(p);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    threadFailed.set(true);
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (threadFailed.get()) {
            throw new AssertionError("Multi-threaded stress test failed");
        }

        // Test StdLib string shims
        ManagedPointer<?> s1 = allocator.malloc(32);
        ManagedPointer<?> s2 = allocator.malloc(32);
        s1.writeCString("Hello");
        s2.writeCString(" World");

        if (StdLib.strlen(s1) != 5) throw new AssertionError("strlen failed");
        if (StdLib.strlen(s2) != 6) throw new AssertionError("strlen failed for s2");

        ManagedPointer<?> sDest = allocator.malloc(64);
        StdLib.strcpy(sDest, s1);
        if (!sDest.readCString().equals("Hello")) throw new AssertionError("strcpy failed");

        StdLib.strcat(sDest, s2);
        if (!sDest.readCString().equals("Hello World")) throw new AssertionError("strcat failed");

        if (StdLib.strcmp(s1, s1) != 0) throw new AssertionError("strcmp equal failed");
        if (StdLib.strcmp(s1, s2) <= 0) throw new AssertionError("strcmp comparison failed");

        ManagedPointer<?> s3 = allocator.malloc(32);
        StdLib.strncpy(s3, sDest, 5);
        s3.addOffset(5).writeByte((byte) 0); // null terminate
        if (!s3.readCString().equals("Hello")) throw new AssertionError("strncpy failed");

        if (StdLib.strncmp(s1, sDest, 5) != 0) throw new AssertionError("strncmp failed");

        allocator.free(s1);
        allocator.free(s2);
        allocator.free(sDest);
        allocator.free(s3);

        // Test pointer alignment and aligned_alloc
        ManagedPointer<?> pAligned = allocator.aligned_alloc(16, 64);
        if (!pAligned.isAligned(16)) throw new AssertionError("aligned_alloc not 16-byte aligned");
        if (pAligned.getBounds() - pAligned.getOffset() != 64) throw new AssertionError("aligned_alloc length mismatch");

        ManagedPointer<?> unaligned = pAligned.addOffset(3);
        if (unaligned.isAligned(16)) throw new AssertionError("unaligned pointer should not be 16-aligned");
        ManagedPointer<?> reAligned = unaligned.align(16);
        if (!reAligned.isAligned(16)) throw new AssertionError("reAligned should be 16-aligned");

        // Test StructLayout
        // struct Entity { int id; byte flag; double score; }
        StructLayout layout = StructLayout.builder()
                .addField("id", StructLayout.Type.INT)       // 4 bytes, offset 0
                .addField("flag", StructLayout.Type.BYTE)     // 1 byte, offset 4
                .addField("score", StructLayout.Type.DOUBLE)  // 8 bytes, offset 8 (aligned to 8)
                .build();

        if (layout.getOffset("id") != 0) throw new AssertionError("Struct field id offset mismatch");
        if (layout.getOffset("flag") != 4) throw new AssertionError("Struct field flag offset mismatch");
        if (layout.getOffset("score") != 8) throw new AssertionError("Struct field score offset mismatch");
        if (layout.getSize() != 16) throw new AssertionError("Struct total size mismatch: " + layout.getSize());
        if (layout.getAlignment() != 8) throw new AssertionError("Struct alignment mismatch: " + layout.getAlignment());

        ManagedPointer<?> entity = allocator.malloc(layout.getSize());
        layout.writeInt(entity, "id", 1001);
        layout.writeDouble(entity, "score", 99.5);

        if (layout.readInt(entity, "id") != 1001) throw new AssertionError("Struct readInt mismatch");
        if (layout.readDouble(entity, "score") != 99.5) throw new AssertionError("Struct readDouble mismatch");

        allocator.free(entity);

        System.out.println("All tests passed!");
    }
}
