package org.jvmcpp.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Models the C++ Virtual Method Table (vtable) dispatch mechanism for compiled JVM-C++ code.
 * Each virtual class layout points to a registered VTable ID at offset 0.
 *
 * Supports:
 * - VTable inheritance (inheriting parent method slots and signature indices)
 * - Virtual method overriding at specific slot indices
 * - Pure virtual method slots
 * - Method slot counting and inspection
 */
public final class VTable {

    @FunctionalInterface
    public interface VirtualMethod {
        Object invoke(ManagedPointer<?> thisPtr, Object... args) throws Throwable;
    }

    private static final Map<Long, VTable> VTABLE_REGISTRY = new ConcurrentHashMap<>();
    private static long nextVTableId = 1;

    private final long id;
    private final String className;
    private final VTable parentVTable;
    private final List<VirtualMethod> methodTable = new ArrayList<>();

    public VTable(String className) {
        this(className, null);
    }

    public VTable(String className, VTable parentVTable) {
        synchronized (VTable.class) {
            this.id = nextVTableId++;
        }
        this.className = className;
        this.parentVTable = parentVTable;

        if (parentVTable != null) {
            synchronized (parentVTable) {
                this.methodTable.addAll(parentVTable.methodTable);
            }
        }

        VTABLE_REGISTRY.put(this.id, this);
    }

    public long getId() {
        return id;
    }

    public String getClassName() {
        return className;
    }

    public VTable getParentVTable() {
        return parentVTable;
    }

    /**
     * Checks if this VTable belongs to a class that is equal to or derived from the specified parent VTable.
     */
    public boolean isSubclassOf(VTable target) {
        if (target == null) return false;
        VTable curr = this;
        while (curr != null) {
            if (curr.id == target.id || curr.className.equals(target.className)) {
                return true;
            }
            curr = curr.parentVTable;
        }
        return false;
    }

    /**
     * Checks if this VTable belongs to a class that is equal to or derived from the specified class name.
     */
    public boolean isSubclassOf(String targetClassName) {
        if (targetClassName == null) return false;
        VTable curr = this;
        while (curr != null) {
            if (curr.className.equals(targetClassName)) {
                return true;
            }
            curr = curr.parentVTable;
        }
        return false;
    }

    public synchronized int getMethodCount() {
        return methodTable.size();
    }

    public synchronized int registerMethod(VirtualMethod method) {
        int index = methodTable.size();
        methodTable.add(method);
        return index;
    }

    public synchronized void setMethod(int index, VirtualMethod method) {
        while (methodTable.size() <= index) {
            methodTable.add(null);
        }
        methodTable.set(index, method);
    }

    public VirtualMethod getMethod(int index) {
        if (index < 0 || index >= methodTable.size()) {
            throw new IndexOutOfBoundsException("Invalid vtable method index: " + index + " for class " + className);
        }
        VirtualMethod m = methodTable.get(index);
        if (m == null) {
            throw new IllegalStateException("Pure virtual method called or uninitialized slot: " + index + " in class " + className);
        }
        return m;
    }

    public Object invoke(int index, ManagedPointer<?> thisPtr, Object... args) {
        try {
            return getMethod(index).invoke(thisPtr, args);
        } catch (Throwable t) {
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            throw new RuntimeException("Virtual method invocation failed on " + className + " at slot " + index, t);
        }
    }

    public static VTable getById(long id) {
        VTable vt = VTABLE_REGISTRY.get(id);
        if (vt == null) {
            throw new IllegalArgumentException("Unknown vtable ID: " + id);
        }
        return vt;
    }
}
