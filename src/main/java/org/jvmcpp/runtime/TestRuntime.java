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

        System.out.println("All tests passed!");
    }
}
