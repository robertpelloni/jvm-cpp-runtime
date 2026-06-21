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

        System.out.println("All tests passed!");
    }
}
