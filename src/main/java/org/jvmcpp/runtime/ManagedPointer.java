package org.jvmcpp.runtime;

import sun.misc.Unsafe;
import java.lang.reflect.Field;

public final class ManagedPointer<T> {
    private final Object base;
    private long offset;
    private final long bounds;

    private static final Unsafe UNSAFE;
    private static final long BYTE_ARRAY_OFFSET;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
            BYTE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ManagedPointer(Object base, long offset, long bounds) {
        this.base = base;
        this.offset = offset;
        this.bounds = bounds;
    }

    private void checkBounds(long size) {
        if (offset < 0 || offset + size > bounds) {
            throw new MemoryAccessException("Memory access out of bounds. Offset: " + offset + ", size: " + size + ", bounds: " + bounds);
        }
    }

    private long resolveAddress() {
        if (base instanceof byte[]) {
            return BYTE_ARRAY_OFFSET + offset;
        } else if (base == null) {
            return offset;
        } else {
            return offset; // Logical fallback
        }
    }

    public byte readByte() {
        checkBounds(1);
        return UNSAFE.getByte(base, resolveAddress());
    }

    public void writeByte(byte value) {
        checkBounds(1);
        UNSAFE.putByte(base, resolveAddress(), value);
    }

    public int readInt() {
        checkBounds(4);
        return UNSAFE.getInt(base, resolveAddress());
    }

    public void writeInt(int value) {
        checkBounds(4);
        UNSAFE.putInt(base, resolveAddress(), value);
    }

    public long readLong() {
        checkBounds(8);
        return UNSAFE.getLong(base, resolveAddress());
    }

    public void writeLong(long value) {
        checkBounds(8);
        UNSAFE.putLong(base, resolveAddress(), value);
    }

    public long readPointer() {
        // A C++ pointer is typically represented as a 64-bit integer
        checkBounds(8);
        return UNSAFE.getLong(base, resolveAddress());
    }

    public void writePointer(long value) {
        checkBounds(8);
        UNSAFE.putLong(base, resolveAddress(), value);
    }

    public ManagedPointer<T> addOffset(long bytes) {
        return new ManagedPointer<>(base, offset + bytes, bounds);
    }

    // Getters
    public Object getBase() {
        return base;
    }

    public long getOffset() {
        return offset;
    }

    public long getBounds() {
        return bounds;
    }
}
