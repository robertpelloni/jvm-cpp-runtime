package org.jvmcpp.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Models the C++ Virtual Method Table (vtable) dispatch mechanism for compiled JVM-C++ code.
 * Each virtual class layout points to a registered VTable ID at offset 0.
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
    private final List<VirtualMethod> methodTable = new ArrayList<>();

    public VTable(String className) {
        synchronized (VTable.class) {
            this.id = nextVTableId++;
        }
        this.className = className;
        VTABLE_REGISTRY.put(this.id, this);
    }

    public long getId() {
        return id;
    }

    public String getClassName() {
        return className;
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
            throw new IllegalStateException("Pure virtual method called or uninitialized slot: " + index);
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
            throw new RuntimeException("Virtual method invocation failed on " + className, t);
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
