package org.jvmcpp.runtime;

/**
 * Standard convention for C++ class instance layout in JVM bytecode.
 *
 * Layout Convention:
 * - Offset 0..7: 64-bit primary VTable ID (vptr).
 * - Offset 8..N: Instance member variables or subobjects.
 *
 * Virtual Destructor Convention:
 * - VTable slot 0 is conventionally reserved for the virtual destructor (dtor).
 *   When an object is deleted via a base pointer, slot 0 is dispatched first
 *   to clean up fields / run subclass dtor before freeing memory.
 *
 * Multiple Inheritance / Subobject Thunk Convention:
 * - Secondary base subobjects are embedded at an offset within the complete instance.
 * - `asSubobject(long offset, VTable secondaryVTable)` creates a view pointer to the subobject.
 * - Invocations across secondary bases adjust the this-pointer (thunk adjustment) by `thisOffset`
 *   to point back to the complete object or target base instance.
 */
public class CppObject {

    public static final long VPTR_OFFSET = 0;
    public static final long MEMBERS_OFFSET = 8;
    public static final int VIRTUAL_DTOR_SLOT = 0;

    protected final ManagedPointer<?> memory;
    protected final VTable vtable;
    protected final long thisAdjustmentOffset;

    public CppObject(MemoryAllocator allocator, VTable vtable, long membersSize) {
        this(allocator, vtable, membersSize, 0);
    }

    public CppObject(MemoryAllocator allocator, VTable vtable, long membersSize, long thisAdjustmentOffset) {
        if (allocator == null || vtable == null) {
            throw new IllegalArgumentException("Allocator and VTable cannot be null");
        }
        if (membersSize < 0) {
            throw new IllegalArgumentException("membersSize cannot be negative: " + membersSize);
        }

        // Allocate total instance size: 8 bytes for vptr + member variables
        this.memory = allocator.malloc(MEMBERS_OFFSET + membersSize);
        this.vtable = vtable;
        this.thisAdjustmentOffset = thisAdjustmentOffset;

        // Initialize vptr at offset 0
        this.memory.writePointer(vtable.getId());
    }

    public CppObject(ManagedPointer<?> existingMemory, VTable vtable) {
        this(existingMemory, vtable, 0);
    }

    public CppObject(ManagedPointer<?> existingMemory, VTable vtable, long thisAdjustmentOffset) {
        if (existingMemory == null || vtable == null) {
            throw new IllegalArgumentException("Memory and VTable cannot be null");
        }
        this.memory = existingMemory;
        this.vtable = vtable;
        this.thisAdjustmentOffset = thisAdjustmentOffset;
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

    public long getThisAdjustmentOffset() {
        return thisAdjustmentOffset;
    }

    public ManagedPointer<?> getMembersPointer() {
        return memory.addOffset(MEMBERS_OFFSET);
    }

    /**
     * Checks if this instance is of the specified type or a derived subtype (C++ RTTI / typeid emulation).
     */
    public boolean isInstanceOf(String className) {
        if (className == null) return false;
        long currentVTableId = memory.readPointer();
        VTable currentVTable = VTable.getById(currentVTableId);
        return currentVTable.isSubclassOf(className);
    }

    /**
     * Performs a C++ dynamic_cast equivalent check.
     * If this object's runtime vtable is derived from or matches targetVTable,
     * returns this instance (or an adjusted subobject view). Otherwise returns null.
     */
    public CppObject dynamicCast(VTable targetVTable) {
        if (targetVTable == null) return null;
        long currentVTableId = memory.readPointer();
        VTable currentVTable = VTable.getById(currentVTableId);
        if (currentVTable.isSubclassOf(targetVTable)) {
            return this;
        }
        return null;
    }

    /**
     * Obtains a subobject base pointer representation embedded at the specified offset
     * for multiple inheritance layouts, with an adjusted vptr and thunk adjustment.
     */
    public CppObject asSubobject(long subobjectOffset, VTable subobjectVTable) {
        if (subobjectOffset < 0 || subobjectOffset >= memory.getBounds()) {
            throw new IllegalArgumentException("Invalid subobject offset: " + subobjectOffset);
        }
        ManagedPointer<?> subMemory = memory.slice(subobjectOffset, memory.getBounds() - subobjectOffset);
        return new CppObject(subMemory, subobjectVTable, -subobjectOffset);
    }

    /**
     * Resolves the primary/complete object this pointer by applying this-pointer thunk adjustment.
     */
    public ManagedPointer<?> getCompleteObjectPointer() {
        if (thisAdjustmentOffset == 0) {
            return memory;
        }
        return memory.addOffset(thisAdjustmentOffset);
    }

    /**
     * Invokes a virtual method through the current vtable ID at offset 0.
     * Applies thisAdjustmentOffset if invoked on a subobject base to ensure the
     * implementation method receives the correct complete object this-pointer.
     */
    public Object invokeVirtual(int methodIndex, Object... args) {
        long currentVTableId = memory.readPointer();
        VTable currentVTable = VTable.getById(currentVTableId);
        ManagedPointer<?> targetThis = getCompleteObjectPointer();
        return currentVTable.invoke(methodIndex, targetThis, args);
    }

    /**
     * Dispatches virtual operator== comparison against another object or operand.
     */
    public boolean operatorEquals(int opEqualsSlot, Object operand) {
        Object res = invokeVirtual(opEqualsSlot, operand);
        return Boolean.TRUE.equals(res);
    }

    /**
     * Dispatches virtual operator[] subscript access.
     */
    public Object operatorSubscript(int opSubscriptSlot, long index) {
        return invokeVirtual(opSubscriptSlot, index);
    }

    /**
     * Dispatches virtual binary operator (e.g. operator+, operator-, operator*).
     */
    public Object operatorBinary(int opSlot, Object operand) {
        return invokeVirtual(opSlot, operand);
    }

    /**
     * Dispatches virtual destructor if registered at slot 0, and optionally frees
     * the underlying complete object memory using the provided allocator.
     */
    public void destroy(MemoryAllocator allocator) {
        long currentVTableId = memory.readPointer();
        VTable currentVTable = VTable.getById(currentVTableId);

        // If slot 0 is populated, invoke the virtual destructor
        if (currentVTable.getMethodCount() > VIRTUAL_DTOR_SLOT) {
            try {
                VTable.VirtualMethod dtor = currentVTable.getMethod(VIRTUAL_DTOR_SLOT);
                if (dtor != null) {
                    dtor.invoke(getCompleteObjectPointer());
                }
            } catch (Throwable t) {
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                }
                throw new RuntimeException("Virtual destructor failed during destroy()", t);
            }
        }

        if (allocator != null) {
            ManagedPointer<?> completePtr = getCompleteObjectPointer();
            allocator.free(completePtr);
        }
    }
}
