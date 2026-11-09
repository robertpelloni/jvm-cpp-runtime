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

        testDynamicPointerArithmeticChain(allocator);
        testPrimitiveTypePunningAndEndianness(allocator);
        testPrimitives(allocator);
        testBoundsAndExceptions(allocator);
        testPointerArithmeticAndAlignment(allocator);
        testMemoryAllocatorOperations(allocator);
        testAllocatorQuotaExhaustionRecovery();
        testStdLibStringShims(allocator);
        testStructLayout(allocator);
        testByteBufferAndArrayConversion(allocator);
        testMemoryPoolRecycling(allocator);
        testEdgeConditionsAndBoundaryLimits(allocator);
        testPointerCrossBlockAndStringExceptions(allocator);
        testCppObjectAndVTable(allocator);
        testOffHeapDirectMemory(allocator);
        testDirectMemoryAlignmentAndBounds(allocator);
        testMultidimensionalArrayEmulation(allocator);
        testPointerTypeCastSimulation(allocator);
        testConcurrencyStress(allocator);

        System.out.println("All test suites passed successfully! (" + testsRun + " assertions verified)");
    }

    /**
     * Validates simulated pointer increment/decrement chains (e.g. while(*p++) operations),
     * ensuring that sequential addOffset operations maintain correct pointer displacement,
     * difference calculations, and trigger boundary violations at the exact endpoint.
     */
    private static void testDynamicPointerArithmeticChain(MemoryAllocator allocator) {
        int elementCount = 8;
        ManagedPointer<?> buffer = allocator.malloc(elementCount * 4);

        // Simulate filling array via walking pointer: int* p = buffer; for(...) *p++ = val;
        ManagedPointer<?> walker = buffer;
        for (int i = 0; i < elementCount; i++) {
            walker.writeInt(100 + i);
            walker = walker.addOffset(4);
        }

        // Walker should now be exactly at buffer boundary (offset 32 on 32-byte buffer)
        assertEquals(32L, walker.getOffset(), "Walker offset after chain should be 32");
        assertEquals(32L, walker.diff(buffer), "Walker diff from start should be 32 bytes");

        // Attempting to write at walker should throw MemoryAccessException (at boundary)
        boolean caughtOverflow = false;
        try {
            walker.writeInt(999);
        } catch (MemoryAccessException e) {
            caughtOverflow = true;
        }
        assertTrue(caughtOverflow, "Writing at past-the-end boundary must throw MemoryAccessException");

        // Simulate reading backward: while (walker > buffer) { walker -= 4; read(*walker); }
        for (int i = elementCount - 1; i >= 0; i--) {
            walker = walker.addOffset(-4);
            assertEquals(100 + i, walker.readInt(), "Backward walked value match at index " + i);
        }
        assertEquals(0L, walker.getOffset(), "Walker returned to base offset 0");

        // Decrementing past base offset 0 must throw MemoryAccessException
        boolean caughtUnderflow = false;
        try {
            walker.addOffset(-4).readInt();
        } catch (MemoryAccessException e) {
            caughtUnderflow = true;
        }
        assertTrue(caughtUnderflow, "Decrementing past base offset 0 must throw MemoryAccessException");

        allocator.free(buffer);
    }

    /**
     * Validates type-punning between floating point primitives and bitwise integer representations
     * (e.g. float vs uint32_t, double vs uint64_t) to simulate low-level fast inverse square root
     * or IEEE 754 inspection in compiled C++ code.
     */
    private static void testPrimitiveTypePunningAndEndianness(MemoryAllocator allocator) {
        ManagedPointer<?> mem = allocator.malloc(16);

        // Float / Int bit punning
        float testFloat = -13.375f;
        int expectedIntBits = Float.floatToIntBits(testFloat);
        mem.writeFloat(testFloat);
        assertEquals(expectedIntBits, mem.readInt(), "Float written as float should read expected bit pattern as int");

        // Double / Long bit punning
        double testDouble = 1.0 / 3.0;
        long expectedLongBits = Double.doubleToLongBits(testDouble);
        mem.writeDouble(testDouble);
        assertEquals(expectedLongBits, mem.readLong(), "Double written as double should read expected bit pattern as long");

        // Overwrite as long and read back as double
        mem.writeLong(expectedLongBits);
        assertEquals(testDouble, mem.readDouble(), "Long written as bits should read back exact double value");

        allocator.free(mem);
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

    /**
     * Validates quota boundary tracking, memory exhaustion, partial deallocation recovery,
     * and exact boundary allocations up to maxHeapBytes limit.
     */
    private static void testAllocatorQuotaExhaustionRecovery() {
        long quotaLimit = 128;
        MemoryAllocator quotaAlloc = new MemoryAllocator(quotaLimit);
        assertEquals(0L, quotaAlloc.getTotalAllocatedBytes(), "Initial allocated bytes should be 0");
        assertEquals(0L, quotaAlloc.getActiveAllocationCount(), "Initial active count should be 0");

        // Allocate block A of 64 bytes
        ManagedPointer<?> blockA = quotaAlloc.malloc(64);
        assertEquals(64L, quotaAlloc.getTotalAllocatedBytes(), "Allocated bytes after block A");
        assertEquals(1L, quotaAlloc.getActiveAllocationCount(), "Active allocations after block A");

        // Allocate block B of 64 bytes (total = 128 == quotaLimit)
        ManagedPointer<?> blockB = quotaAlloc.malloc(64);
        assertEquals(128L, quotaAlloc.getTotalAllocatedBytes(), "Allocated bytes at full quota");
        assertEquals(2L, quotaAlloc.getActiveAllocationCount(), "Active count at full quota");

        // Attempting to allocate even 1 additional byte must fail
        boolean quotaFailed = false;
        try {
            quotaAlloc.malloc(1);
        } catch (MemoryAccessException e) {
            quotaFailed = true;
        }
        assertTrue(quotaFailed, "Allocating beyond quota must throw MemoryAccessException");
        assertEquals(128L, quotaAlloc.getTotalAllocatedBytes(), "Allocated bytes should remain unchanged after failed malloc");

        // Free block A (64 bytes freed)
        quotaAlloc.free(blockA);
        assertEquals(64L, quotaAlloc.getTotalAllocatedBytes(), "Allocated bytes after freeing block A");
        assertEquals(1L, quotaAlloc.getActiveAllocationCount(), "Active count after freeing block A");

        // Reallocate block C of 32 bytes (64 + 32 = 96 <= 128)
        ManagedPointer<?> blockC = quotaAlloc.malloc(32);
        assertEquals(96L, quotaAlloc.getTotalAllocatedBytes(), "Allocated bytes after block C");

        // Allocate block D of 32 bytes (96 + 32 = 128 == 128)
        ManagedPointer<?> blockD = quotaAlloc.malloc(32);
        assertEquals(128L, quotaAlloc.getTotalAllocatedBytes(), "Allocated bytes back at quota limit");

        // Clean up remaining blocks
        quotaAlloc.free(blockB);
        quotaAlloc.free(blockC);
        quotaAlloc.free(blockD);

        assertEquals(0L, quotaAlloc.getTotalAllocatedBytes(), "Allocated bytes should be 0 after full cleanup");
        assertEquals(0L, quotaAlloc.getActiveAllocationCount(), "Active count should be 0 after full cleanup");
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

        // sprintf
        ManagedPointer<?> sprBuf = allocator.malloc(64);
        int written = StdLib.sprintf(sprBuf, "Code %d: %.2f", 404, 3.14);
        assertTrue(written > 0, "sprintf returned written bytes");
        assertEquals("Code 404: 3.14", sprBuf.readCString(), "sprintf output mismatch");

        // sprintf overflow check
        ManagedPointer<?> tinyBuf = allocator.malloc(4);
        boolean caught = false;
        try {
            StdLib.sprintf(tinyBuf, "Long string exceeding bounds");
        } catch (MemoryAccessException e) {
            caught = true;
        }
        assertTrue(caught, "sprintf should throw on buffer overflow");

        // math.h routines
        assertEquals(5.0, StdLib.fabs(-5.0), "fabs");
        assertEquals(3.0, StdLib.sqrt(9.0), "sqrt");
        assertEquals(8.0, StdLib.pow(2.0, 3.0), "pow");
        assertEquals(0.0, StdLib.sin(0.0), "sin");
        assertEquals(1.0, StdLib.cos(0.0), "cos");
        assertEquals(2.0, StdLib.floor(2.9), "floor");
        assertEquals(3.0, StdLib.ceil(2.1), "ceil");

        // stdlib.h numeric parsing
        ManagedPointer<?> numStr = allocator.malloc(32);
        numStr.writeCString("-12345");
        assertEquals(-12345, StdLib.atoi(numStr), "atoi negative integer");
        numStr.writeCString("9876543210");
        assertEquals(9876543210L, StdLib.atol(numStr), "atol long integer");
        numStr.writeCString("3.14159");
        assertEquals(3.14159, StdLib.atof(numStr), "atof floating point");

        // stdlib.h rand and srand
        StdLib.srand(42);
        int r1 = StdLib.rand();
        int r2 = StdLib.rand();
        assertTrue(r1 >= 0 && r1 < 32768, "rand() within 0..32767 range");
        StdLib.srand(42);
        assertEquals(r1, StdLib.rand(), "srand deterministic reproducibility");

        // stdlib.h qsort and bsearch
        int count = 5;
        ManagedPointer<?> sortArr = allocator.malloc(count * 4);
        int[] rawValues = { 50, 10, 40, 20, 30 };
        for (int i = 0; i < count; i++) {
            sortArr.addOffset(i * 4).writeInt(rawValues[i]);
        }

        StdLib.CmpFunc intCmp = (a, b) -> Integer.compare(a.readInt(), b.readInt());
        StdLib.qsort(sortArr, count, 4, intCmp);

        for (int i = 0; i < count; i++) {
            assertEquals((i + 1) * 10, sortArr.addOffset(i * 4).readInt(), "qsort array element sorted at " + i);
        }

        ManagedPointer<?> searchKey = allocator.malloc(4);
        searchKey.writeInt(30);
        ManagedPointer<?> foundPtr = StdLib.bsearch(searchKey, sortArr, count, 4, intCmp);
        assertTrue(foundPtr != null, "bsearch found key");
        assertEquals(30, foundPtr.readInt(), "bsearch read match");
        assertEquals(2 * 4L, foundPtr.diff(sortArr), "bsearch index distance check");

        searchKey.writeInt(99);
        ManagedPointer<?> notFound = StdLib.bsearch(searchKey, sortArr, count, 4, intCmp);
        assertTrue(notFound == null, "bsearch returns null on missing element");

        allocator.free(numStr);
        allocator.free(sortArr);
        allocator.free(searchKey);

        allocator.free(str1);
        allocator.free(str2);
        allocator.free(dest);
        allocator.free(sprBuf);
        allocator.free(tinyBuf);
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

    private static void testEdgeConditionsAndBoundaryLimits(MemoryAllocator allocator) {
        ManagedPointer<?> block = allocator.malloc(8);

        // Valid read at the very last byte (offset 7, size 1)
        block.addOffset(7).writeByte((byte) 99);
        assertEquals((byte) 99, block.addOffset(7).readByte(), "Boundary 1-byte read");

        // Invalid 2-byte read at offset 7 (7 + 2 = 9 > 8)
        boolean caught = false;
        try {
            block.addOffset(7).readShort();
        } catch (MemoryAccessException e) {
            caught = true;
        }
        assertTrue(caught, "Should throw for 2-byte read at boundary offset 7");

        // Invalid 4-byte read at offset 5 (5 + 4 = 9 > 8)
        caught = false;
        try {
            block.addOffset(5).readInt();
        } catch (MemoryAccessException e) {
            caught = true;
        }
        assertTrue(caught, "Should throw for 4-byte read at boundary offset 5");

        // Invalid 8-byte read at offset 1 (1 + 8 = 9 > 8)
        caught = false;
        try {
            block.addOffset(1).readLong();
        } catch (MemoryAccessException e) {
            caught = true;
        }
        assertTrue(caught, "Should throw for 8-byte read at boundary offset 1");

        // Zero-length allocations, slices, and fills
        ManagedPointer<?> zeroPtr = allocator.malloc(0);
        assertEquals(0L, zeroPtr.getBounds(), "Zero length bounds");
        zeroPtr.fillMemory((byte) 0, 0); // No-op, should not throw
        MemoryAllocator.copyMemory(zeroPtr, zeroPtr, 0); // No-op, should not throw

        ManagedPointer<?> zeroSlice = block.slice(0, 0);
        assertEquals(0L, zeroSlice.getBounds() - zeroSlice.getOffset(), "Zero length slice");

        allocator.free(block);
        allocator.free(zeroPtr);
    }

    private static void testPointerCrossBlockAndStringExceptions(MemoryAllocator allocator) {
        ManagedPointer<?> block1 = allocator.malloc(16);
        ManagedPointer<?> block2 = allocator.malloc(16);

        // Cross-block diff check
        boolean caught = false;
        try {
            block1.diff(block2);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught, "diff between different memory blocks should throw IllegalArgumentException");

        // Cross-block compareTo check
        caught = false;
        try {
            ((ManagedPointer) block1).compareTo(block2);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught, "compareTo between different memory blocks should throw IllegalArgumentException");

        // Unterminated C-string check
        block1.fillMemory((byte) 'X', 16); // no null terminator
        caught = false;
        try {
            block1.readCString();
        } catch (MemoryAccessException e) {
            caught = true;
        }
        assertTrue(caught, "readCString without null terminator should throw MemoryAccessException");

        // Invalid aligned_alloc alignment
        caught = false;
        try {
            allocator.aligned_alloc(15, 32); // 15 is not a power of two
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught, "aligned_alloc with non-power-of-two alignment should throw IllegalArgumentException");

        // copyMemory out of bounds check
        caught = false;
        try {
            MemoryAllocator.copyMemory(block1, block2, 32); // 32 > 16 bounds
        } catch (MemoryAccessException e) {
            caught = true;
        }
        assertTrue(caught, "copyMemory exceeding bounds should throw MemoryAccessException");

        allocator.free(block1);
        allocator.free(block2);
    }

    private static void testCppObjectAndVTable(MemoryAllocator allocator) {
        // Base class: Shape (virtual int area())
        VTable shapeVTable = new VTable("Shape");
        int areaSlot = shapeVTable.registerMethod((thisPtr, args) -> 0);

        // Derived class: Rectangle (overrides area())
        VTable rectVTable = new VTable("Rectangle");
        rectVTable.setMethod(areaSlot, (thisPtr, args) -> {
            // Read member variables width (offset 8) and height (offset 12)
            int width = thisPtr.addOffset(CppObject.MEMBERS_OFFSET).readInt();
            int height = thisPtr.addOffset(CppObject.MEMBERS_OFFSET + 4).readInt();
            return width * height;
        });

        // Instantiate Rectangle object with 8 bytes for members (width + height)
        CppObject rectObj = new CppObject(allocator, rectVTable, 8);
        rectObj.getMembersPointer().writeInt(5); // width = 5
        rectObj.getMembersPointer().addOffset(4).writeInt(10); // height = 10

        assertEquals(rectVTable.getId(), rectObj.getVTableId(), "VTable ID mismatch at offset 0");
        Object result = rectObj.invokeVirtual(areaSlot);
        assertEquals(50, result, "Virtual method area() dispatch mismatch");

        rectObj.destroy(allocator);
    }

    /**
     * Validates direct off-heap native memory alignment checks, byte filling,
     * slicing, and bounds-checking exceptions.
     */
    private static void testDirectMemoryAlignmentAndBounds(MemoryAllocator allocator) {
        ManagedPointer<?> direct = allocator.mallocDirect(64);
        assertTrue(direct.isDirect(), "Direct pointer should report isDirect() == true");
        assertTrue(direct.getRawAddress() != 0, "Direct pointer raw address should be non-zero");

        // Direct memory memset
        direct.fillMemory((byte) 0x7F, 32);
        for (int i = 0; i < 32; i++) {
            assertEquals((byte) 0x7F, direct.addOffset(i).readByte(), "Direct memset byte at " + i);
        }

        // Direct memory slicing
        ManagedPointer<?> slice = direct.slice(16, 32);
        assertTrue(slice.isDirect(), "Sliced direct pointer should be direct");
        assertEquals(16L, slice.getOffset(), "Sliced direct pointer offset");
        assertEquals(48L, slice.getBounds(), "Sliced direct pointer bounds");

        slice.writeLong(0x123456789ABCDEF0L);
        assertEquals(0x123456789ABCDEF0L, direct.addOffset(16).readLong(), "Parent direct memory reflects slice write");

        // Direct memory bounds checking: reading past slice boundary (offset 48 on direct block)
        boolean caughtSliceOverflow = false;
        try {
            slice.addOffset(32).readByte(); // 16 + 32 = 48, which reaches upper bounds 48
        } catch (MemoryAccessException e) {
            caughtSliceOverflow = true;
        }
        assertTrue(caughtSliceOverflow, "Reading past direct slice boundary must throw MemoryAccessException");

        allocator.free(direct);
    }

    private static void testOffHeapDirectMemory(MemoryAllocator allocator) {
        ManagedPointer<?> directPtr = allocator.mallocDirect(64);
        assertTrue(directPtr.isDirect(), "mallocDirect pointer should be direct");
        assertEquals(null, directPtr.getBase(), "Direct pointer base should be null");
        assertTrue(directPtr.getRawAddress() != 0, "Direct pointer raw address should be non-zero");

        // Test writing and reading primitives off-heap
        directPtr.writeInt(999999);
        assertEquals(999999, directPtr.readInt(), "Direct pointer readInt mismatch");

        directPtr.writeDouble(12345.6789);
        assertEquals(12345.6789, directPtr.readDouble(), "Direct pointer readDouble mismatch");

        // Pointer arithmetic on direct pointer
        ManagedPointer<?> offsetDirect = directPtr.addOffset(8);
        offsetDirect.writeLong(0xAABBCCDDEEFF0011L);
        assertEquals(0xAABBCCDDEEFF0011L, offsetDirect.readLong(), "Offset direct readLong mismatch");

        allocator.free(directPtr);

        // Double free check on direct memory
        boolean caught = false;
        try {
            allocator.free(directPtr);
        } catch (MemoryAccessException e) {
            caught = true;
        }
        assertTrue(caught, "Should throw on double free of direct memory");
    }

    private static void testMemoryPoolRecycling(MemoryAllocator allocator) {
        ManagedPointer<?> p1 = allocator.allocatePooled(64);
        p1.writeLong(0x1122334455667788L);
        Object base1 = p1.getBase();

        allocator.freePooled(p1);

        // Next allocation of same size should recycle the same underlying base array
        ManagedPointer<?> p2 = allocator.allocatePooled(64);
        assertEquals(base1, p2.getBase(), "allocatePooled should reuse block from pool");
        assertEquals(0L, p2.getOffset(), "reused pooled block offset should be reset to 0");
        assertEquals((byte) 0, p2.readByte(), "reused pooled block should be sanitized/zeroed");

        // Test quota enforcement on pooled reuse
        MemoryAllocator quotaAllocator = new MemoryAllocator(64);
        ManagedPointer<?> q1 = quotaAllocator.malloc(64);
        quotaAllocator.freePooled(q1);

        ManagedPointer<?> q2 = quotaAllocator.malloc(64);
        boolean quotaCaught = false;
        try {
            quotaAllocator.allocatePooled(64); // pool has block, but quota is exceeded (64 + 64 > 64)
        } catch (MemoryAccessException e) {
            quotaCaught = true;
        }
        assertTrue(quotaCaught, "allocatePooled should enforce heap quota before reusing");
        quotaAllocator.free(q2);

        // Test freeing via a sliced pointer decrements the exact allocated block size
        long bytesBefore = allocator.getTotalAllocatedBytes();
        long allocsBefore = allocator.getActiveAllocationCount();
        ManagedPointer<?> fullBlock = allocator.malloc(128);
        ManagedPointer<?> subSlice = fullBlock.slice(10, 20); // bounds = 30, length = 20
        allocator.free(subSlice); // should free the underlying 128-byte block accurately
        assertEquals(bytesBefore, allocator.getTotalAllocatedBytes(), "Total allocated bytes restored after sliced free");
        assertEquals(allocsBefore, allocator.getActiveAllocationCount(), "Active allocations restored after sliced free");

        allocator.free(p2);
    }

    private static void testMultidimensionalArrayEmulation(MemoryAllocator allocator) {
        // Emulates int matrix[3][4] (12 ints = 48 bytes)
        int rows = 3;
        int cols = 4;
        ManagedPointer<?> matrix = allocator.malloc((long) rows * cols * 4);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                long offset = ((long) r * cols + c) * 4;
                matrix.addOffset(offset).writeInt(r * 100 + c);
            }
        }

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                long offset = ((long) r * cols + c) * 4;
                int val = matrix.addOffset(offset).readInt();
                assertEquals(r * 100 + c, val, "Matrix element at [" + r + "][" + c + "]");
            }
        }

        allocator.free(matrix);
    }

    private static void testPointerTypeCastSimulation(MemoryAllocator allocator) {
        // Emulates union { int i; byte b[4]; }
        ManagedPointer<?> pun = allocator.malloc(4);
        pun.writeInt(0x12345678);

        // Verify individual byte reads regardless of endianness
        byte b0 = pun.readByte();
        byte b1 = pun.addOffset(1).readByte();
        byte b2 = pun.addOffset(2).readByte();
        byte b3 = pun.addOffset(3).readByte();

        int reconstructed = (java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.LITTLE_ENDIAN)
                ? ((b0 & 0xFF) | ((b1 & 0xFF) << 8) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 24))
                : (((b0 & 0xFF) << 24) | ((b1 & 0xFF) << 16) | ((b2 & 0xFF) << 8) | (b3 & 0xFF));

        assertEquals(0x12345678, reconstructed, "Type-punning byte reconstruction match");
        allocator.free(pun);
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
