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

        // Test free
        allocator.free(ptr);
        if (ptr.readByte() != 0) throw new AssertionError("Free did not clear memory");

        System.out.println("All tests passed!");
    }
}
