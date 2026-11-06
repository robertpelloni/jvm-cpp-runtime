package org.jvmcpp.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestRuntime {

    private static int testsRun = 0;

    private static void assertTrue(boolean condition, String message) {
        testsRun++;
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        testsRun++;
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("Assertion failed: " + message + " (Expected: " + expected + ", Actual: " + actual + ")");
    }

    public static void main(String[] args) {
        MemoryAllocator allocator = new MemoryAllocator();

        testPrimitives(allocator);
        testBoundsAndExceptions(allocator);
        testPointerArithmeticAndAlignment(allocator);
        testMemoryAllocatorOperations(allocator);
        testStdLibStringShims(allocator);
        testStructLayout(allocator);
        testByteBufferAndArrayConversion(allocator);
        testConcurrencyStress(allocator);

        System.out.println("All test suites passed successfully! (" + testsRun + " assertions verified)");
    }

    private static void testPrimitives(MemoryAllocator allocator) {
        ManagedPointer<?> ptr = allocator.malloc(64);

        ptr.writeByte((byte) 42);
        assertEquals((byte) 42, ptr.readByte(), "Byte read/write mismatch");

        ptr.writeBoolean(true);
        assertTrue(ptr.readBoolean(), "Boolean true mismatch");
        ptr.writeBoolean(false);
        assertTrue(!ptr.readBoolean(), "Boolean false mismatch");

        ptr.writeChar('A');
        assertEquals('A', ptr.readChar(), "Char mismatch");

        ptr.writeShort((short) 1234);
        assertEquals((short) 1234, ptr.readShort(), "Short mismatch");

        ptr.writeInt(123456789);
        assertEquals(123456789, ptr.readInt(), "Int mismatch");

        ptr.writeLong(9876543210L);
        assertEquals(9876543210L, ptr.readLong(), "Long mismatch");

        ptr.writeFloat(3.14159f);
        assertEquals(3.14159f, ptr.readFloat(), "Float mismatch");

        ptr.writeDouble(2.718281828);
        assertEquals(2.718281828, ptr.readDouble(), "Double mismatch");

        ptr.writePointer(0xDEADBEEFCAFEBABEL);
        assertEquals(0xDEADBEEFCAFEBABEL, ptr.readPointer(), "Pointer mismatch");

        allocator.free(ptr);
    }

    private static void testBoundsAndExceptions(MemoryAllocator allocator) {
        ManagedPointer<?> ptr = allocator.malloc(32);

        // Positive boundary check
        boolean caught = false;
        try {
            ptr.addOffset(30).readInt(); // 30 + 4 = 34 > 32
        } catch (MemoryAccessException e) {
            caught = true;
        }
        assertTrue(caught, "Should throw on upper bound overflow");

        // Negative boundary check
        caught = false;
        try {
            ptr.addOffset(-1).readByte();
        } catch (MemoryAccessException e) {
            caught = true;
        }
        assertTrue(caught, "Should throw on negative offset");

        // Sub-slice out of bounds check
        caught = false;
        try {
            ptr.slice(20, 20); // 20 + 20 = 40 > 32
        } catch (MemoryAccessException e) {
            caught = true;
        }
        assertTrue(caught, "Should throw on slice exceeding bounds");

        allocator.free(ptr);
    }

    private static void testPointerArithmeticAndAlignment(MemoryAllocator allocator) {
        ManagedPointer<?> block = allocator.malloc(64);

        ManagedPointer<?> p1 = block.addOffset(8);
        ManagedPointer<?> p2 = block.addOffset(24);

        assertEquals(16L, p2.diff(p1), "Pointer diff mismatch");
        assertEquals(-16L, p1.diff(p2), "Pointer reverse diff mismatch");

        assertTrue(((ManagedPointer) p1).compareTo(p2) < 0, "p1 should be < p2");
        assertTrue(((ManagedPointer) p2).compareTo(p1) > 0, "p2 should be > p1");
        assertEquals(0, ((ManagedPointer) p1).compareTo(p1), "p1 compareTo p1 should be 0");

        ManagedPointer<?> p1Copy = block.addOffset(8);
        assertEquals(p1, p1Copy, "p1 should equal p1Copy");
        assertEquals(p1.hashCode(), p1Copy.hashCode(), "hashCodes should match");

        // Slicing
        ManagedPointer<?> slice = block.slice(10, 20);
        assertEquals(10L, slice.getOffset(), "Slice offset mismatch");
        assertEquals(30L, slice.getBounds(), "Slice bounds mismatch");

        // Nested slicing
        ManagedPointer<?> nestedSlice = slice.slice(5, 10);
        assertEquals(15L, nestedSlice.getOffset(), "Nested slice offset mismatch");
        assertEquals(25L, nestedSlice.getBounds(), "Nested slice bounds mismatch");

        // Alignment
        ManagedPointer<?> alignedAlloc = allocator.aligned_alloc(32, 64);
        assertTrue(alignedAlloc.isAligned(32), "aligned_alloc should satisfy 32-byte alignment");

        ManagedPointer<?> unaligned = alignedAlloc.addOffset(7);
        assertTrue(!unaligned.isAligned(32), "Offset by 7 should not be 32-aligned");
        ManagedPointer<?> realigned = unaligned.align(32);
        assertTrue(realigned.isAligned(32), "realigned pointer should be 32-aligned");

        allocator.free(block);
    }

    private static void testMemoryAllocatorOperations(MemoryAllocator allocator) {
        // calloc
        ManagedPointer<?> zeroBlock = allocator.calloc(8, 4); // 32 bytes
        assertEquals(32L, zeroBlock.getBounds(), "calloc bounds mismatch");
        for (int i = 0; i < 32; i++) {
            assertEquals((byte) 0, zeroBlock.addOffset(i).readByte(), "calloc byte not zero at " + i);
        }

        // fillMemory / memset
        zeroBlock.fillMemory((byte) 0xCC, 16);
        for (int i = 0; i < 16; i++) {
            assertEquals((byte) 0xCC, zeroBlock.addOffset(i).readByte(), "fillMemory byte mismatch at " + i);
        }

        // copyMemory / memcpy
        ManagedPointer<?> copyDest = allocator.malloc(32);
        MemoryAllocator.copyMemory(copyDest, zeroBlock, 16);
        for (int i = 0; i < 16; i++) {
            assertEquals((byte) 0xCC, copyDest.addOffset(i).readByte(), "copyMemory byte mismatch at " + i);
        }

        // realloc
        ManagedPointer<?> initial = allocator.malloc(8);
        initial.writeInt(8888);
        ManagedPointer<?> grown = allocator.realloc(initial, 32);
        assertEquals(32L, grown.getBounds(), "realloc bounds mismatch");
        assertEquals(8888, grown.readInt(), "realloc data preservation mismatch");

        ManagedPointer<?> shrunk = allocator.realloc(grown, 4);
        assertEquals(4L, shrunk.getBounds(), "realloc shrink bounds mismatch");
        assertEquals(8888, shrunk.readInt(), "realloc shrink data preservation mismatch");

        allocator.free(zeroBlock);
        allocator.free(copyDest);
        allocator.free(shrunk);

        // Double free detection
        boolean caught = false;
        try {
            allocator.free(zeroBlock);
        } catch (MemoryAccessException e) {
            caught = true;
        }
        assertTrue(caught, "Should throw on double free");

        // Quota limits
        MemoryAllocator bounded = new MemoryAllocator(100);
        ManagedPointer<?> p = bounded.malloc(80);
        caught = false;
        try {
            bounded.malloc(30); // 80 + 30 = 110 > 100
        } catch (MemoryAccessException e) {
            caught = true;
        }
        assertTrue(caught, "Should throw on heap quota exceeded");
        bounded.free(p);
    }

    private static void testStdLibStringShims(MemoryAllocator allocator) {
        ManagedPointer<?> str1 = allocator.malloc(32);
        ManagedPointer<?> str2 = allocator.malloc(32);
        str1.writeCString("Foo");
        str2.writeCString("Bar");

        assertEquals(3L, StdLib.strlen(str1), "strlen mismatch for Foo");
        assertEquals(3L, StdLib.strlen(str2), "strlen mismatch for Bar");

        ManagedPointer<?> dest = allocator.malloc(64);
        StdLib.strcpy(dest, str1);
        assertEquals("Foo", dest.readCString(), "strcpy mismatch");

        StdLib.strcat(dest, str2);
        assertEquals("FooBar", dest.readCString(), "strcat mismatch");

        assertTrue(StdLib.strcmp(str1, str2) > 0, "Foo should be > Bar");
        assertEquals(0, StdLib.strncmp(dest, str1, 3), "strncmp match");

        allocator.free(str1);
        allocator.free(str2);
        allocator.free(dest);
    }

    private static void testStructLayout(MemoryAllocator allocator) {
        StructLayout layout = StructLayout.builder()
                .addField("tag", StructLayout.Type.BYTE)       // 1 byte, offset 0
                .addField("id", StructLayout.Type.INT)         // 4 bytes, offset 4 (aligned to 4)
                .addField("timestamp", StructLayout.Type.LONG) // 8 bytes, offset 8 (aligned to 8)
                .build();

        assertEquals(0L, layout.getOffset("tag"), "tag offset");
        assertEquals(4L, layout.getOffset("id"), "id offset");
        assertEquals(8L, layout.getOffset("timestamp"), "timestamp offset");
        assertEquals(16L, layout.getSize(), "struct size");

        ManagedPointer<?> structMem = allocator.malloc(layout.getSize());
        layout.writeInt(structMem, "id", 777);
        layout.writeLong(structMem, "timestamp", 1234567890123L);

        assertEquals(777, layout.readInt(structMem, "id"), "readInt id");
        assertEquals(1234567890123L, layout.readLong(structMem, "timestamp"), "readLong timestamp");

        allocator.free(structMem);
    }

    private static void testByteBufferAndArrayConversion(MemoryAllocator allocator) {
        ManagedPointer<?> ptr = allocator.malloc(16);
        ptr.writeInt(0x01020304);

        ByteBuffer bb = ptr.asByteBuffer();
        bb.order(ByteOrder.nativeOrder());
        assertEquals(0x01020304, bb.getInt(0), "ByteBuffer int mismatch");

        byte[] array = ptr.toByteArray();
        assertEquals(16, array.length, "toByteArray length mismatch");

        allocator.free(ptr);
    }

    private static void testConcurrencyStress(MemoryAllocator allocator) {
        int threadsCount = 4;
        Thread[] threads = new Thread[threadsCount];
        final AtomicBoolean failed = new AtomicBoolean(false);

        for (int i = 0; i < threadsCount; i++) {
            final int tid = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int iter = 0; iter < 100; iter++) {
                        ManagedPointer<?> p = allocator.malloc(32);
                        p.writeLong(tid * 1000L + iter);
                        if (p.readLong() != tid * 1000L + iter) {
                            failed.set(true);
                        }
                        allocator.free(p);
                    }
                } catch (Exception e) {
                    failed.set(true);
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

        assertTrue(!failed.get(), "Concurrency stress test failed");
    }
}
