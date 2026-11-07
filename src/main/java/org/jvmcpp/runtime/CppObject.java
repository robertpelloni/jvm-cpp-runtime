package org.jvmcpp.runtime;

/**
 * Standard convention for C++ class instance layout in JVM bytecode.
 * Offset 0..7: 64-bit VTable ID (vptr).
 * Offset 8..N: Instance member variables.
 */
public class CppObject {

    public static final long VPTR_OFFSET = 0;
    public static final long MEMBERS_OFFSET = 8;

    protected final ManagedPointer<?> memory;
    protected final VTable vtable;

    public CppObject(MemoryAllocator allocator, VTable vtable, long membersSize) {
        if (allocator == null || vtable == null) {
            throw new IllegalArgumentException("Allocator and VTable cannot be null");
        }
        if (membersSize < 0) {
            throw new IllegalArgumentException("membersSize cannot be negative: " + membersSize);
        }

        // Allocate total instance size: 8 bytes for vptr + member variables
        this.memory = allocator.malloc(MEMBERS_OFFSET + membersSize);
        this.vtable = vtable;

        // Initialize vptr at offset 0
        this.memory.writePointer(vtable.getId());
    }

    public CppObject(ManagedPointer<?> existingMemory, VTable vtable) {
        if (existingMemory == null || vtable == null) {
            throw new IllegalArgumentException("Memory and VTable cannot be null");
        }
        this.memory = existingMemory;
        this.vtable = vtable;
        this.memory.writePointer(vtable.getId());
    }

    public ManagedPointer<?> getMemory() {
        return memory;
    }

    public VTable getVTable() {
        return vtable;
    }

    public long getVTableId() {
        return memory.readPointer();
    }

    public ManagedPointer<?> getMembersPointer() {
        return memory.addOffset(MEMBERS_OFFSET);
    }

    public Object invokeVirtual(int methodIndex, Object... args) {
        long currentVTableId = memory.readPointer();
        VTable currentVTable = VTable.getById(currentVTableId);
        return currentVTable.invoke(methodIndex, memory, args);
    }

    public void destroy(MemoryAllocator allocator) {
        if (allocator != null && memory != null) {
            allocator.free(memory);
        }
    }
}
