package org.jvmcpp.runtime;

import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

public final class ManagedPointer<T> implements Comparable<ManagedPointer<T>> {
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

    public boolean readBoolean() {
        checkBounds(1);
        return UNSAFE.getBoolean(base, resolveAddress());
    }

    public void writeBoolean(boolean value) {
        checkBounds(1);
        UNSAFE.putBoolean(base, resolveAddress(), value);
    }

    public byte readByte() {
        checkBounds(1);
        return UNSAFE.getByte(base, resolveAddress());
    }

    public void writeByte(byte value) {
        checkBounds(1);
        UNSAFE.putByte(base, resolveAddress(), value);
    }

    public char readChar() {
        checkBounds(2);
        return UNSAFE.getChar(base, resolveAddress());
    }

    public void writeChar(char value) {
        checkBounds(2);
        UNSAFE.putChar(base, resolveAddress(), value);
    }

    public short readShort() {
        checkBounds(2);
        return UNSAFE.getShort(base, resolveAddress());
    }

    public void writeShort(short value) {
        checkBounds(2);
        UNSAFE.putShort(base, resolveAddress(), value);
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

    public float readFloat() {
        checkBounds(4);
        return UNSAFE.getFloat(base, resolveAddress());
    }

    public void writeFloat(float value) {
        checkBounds(4);
        UNSAFE.putFloat(base, resolveAddress(), value);
    }

    public double readDouble() {
        checkBounds(8);
        return UNSAFE.getDouble(base, resolveAddress());
    }

    public void writeDouble(double value) {
        checkBounds(8);
        UNSAFE.putDouble(base, resolveAddress(), value);
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

    public String readCString() {
        // Find the null terminator manually safely within bounds
        long currentOffset = this.offset;
        long maxLength = bounds - currentOffset;

        int length = 0;
        while (length < maxLength) {
            byte b = UNSAFE.getByte(base, resolveAddress() + length);
            if (b == 0) {
                break;
            }
            length++;
        }

        if (length == maxLength) {
            throw new MemoryAccessException("Could not find null terminator within bounds for CString. Offset: " + currentOffset + ", bounds: " + bounds);
        }

        byte[] strBytes = new byte[length];
        for (int i = 0; i < length; i++) {
            strBytes[i] = UNSAFE.getByte(base, resolveAddress() + i);
        }

        return new String(strBytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public void writeCString(String str) {
        byte[] strBytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        checkBounds(strBytes.length + 1); // +1 for null terminator

        for (int i = 0; i < strBytes.length; i++) {
            UNSAFE.putByte(base, resolveAddress() + i, strBytes[i]);
        }
        // Write null terminator
        UNSAFE.putByte(base, resolveAddress() + strBytes.length, (byte) 0);
    }

    public ManagedPointer<T> addOffset(long bytes) {
        long newOffset = offset + bytes;
        return new ManagedPointer<>(base, newOffset, bounds);
    }

    /**
     * Calculates the byte difference (pointer distance) between this pointer and another.
     * Analogous to (ptr1 - ptr2) in C++. Both pointers must share the same base memory block.
     */
    public long diff(ManagedPointer<?> other) {
        if (other == null) {
            throw new IllegalArgumentException("Cannot calculate pointer diff with null pointer");
        }
        if (this.base != other.base) {
            throw new IllegalArgumentException("Cannot calculate diff between pointers to different memory blocks");
        }
        return this.offset - other.offset;
    }

    /**
     * Creates a sub-range (slice) pointer with restricted bounds.
     */
    public ManagedPointer<T> slice(long sliceOffset, long length) {
        if (sliceOffset < 0 || length < 0 || offset + sliceOffset + length > bounds) {
            throw new MemoryAccessException("Invalid slice range: currentOffset=" + offset + ", sliceOffset=" + sliceOffset + ", length=" + length + ", bounds=" + bounds);
        }
        return new ManagedPointer<>(base, offset + sliceOffset, offset + sliceOffset + length);
    }

    @Override
    public int compareTo(ManagedPointer<T> other) {
        if (other == null) {
            throw new NullPointerException("Cannot compare to null ManagedPointer");
        }
        if (this.base != other.base) {
            throw new IllegalArgumentException("Cannot compare pointers from different memory blocks");
        }
        return Long.compare(this.offset, other.offset);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ManagedPointer<?>)) return false;
        ManagedPointer<?> other = (ManagedPointer<?>) obj;
        return this.base == other.base && this.offset == other.offset && this.bounds == other.bounds;
    }

    @Override
    public int hashCode() {
        int result = (base != null ? System.identityHashCode(base) : 0);
        result = 31 * result + Long.hashCode(offset);
        result = 31 * result + Long.hashCode(bounds);
        return result;
    }

    /**
     * Sets a block of memory to the specified byte value (analogous to C memset).
     *
     * @param value The byte value to write.
     * @param bytes Number of bytes to fill.
     */
    public void fillMemory(byte value, long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Cannot fill negative bytes: " + bytes);
        }
        if (bytes == 0) {
            return;
        }
        checkBounds(bytes);
        UNSAFE.setMemory(base, resolveAddress(), bytes, value);
    }

    // Explicit free manipulation
    public void free() {
        if (base instanceof byte[]) {
            byte[] arr = (byte[]) base;
            for (int i = 0; i < arr.length; i++) {
                arr[i] = 0;
            }
        }
    }

    /**
     * Returns a ByteBuffer wrapping the underlying memory from current offset to bounds.
     * Operates zero-copy when backed by on-heap byte[].
     */
    public ByteBuffer asByteBuffer() {
        if (base instanceof byte[]) {
            byte[] arr = (byte[]) base;
            int pos = (int) offset;
            int len = (int) (bounds - offset);
            return ByteBuffer.wrap(arr, pos, len).slice();
        } else {
            throw new UnsupportedOperationException("Zero-copy ByteBuffer only supported on byte[] backed pointers");
        }
    }

    /**
     * Copies data into a new byte array.
     */
    public byte[] toByteArray() {
        long len = bounds - offset;
        if (len < 0 || len > Integer.MAX_VALUE) {
            throw new MemoryAccessException("Invalid length for toByteArray: " + len);
        }
        byte[] result = new byte[(int) len];
        for (int i = 0; i < (int) len; i++) {
            result[i] = readByte();
            offset++;
        }
        offset -= len; // restore
        return result;
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
