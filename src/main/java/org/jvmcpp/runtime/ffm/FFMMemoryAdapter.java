package org.jvmcpp.runtime.ffm;

import org.jvmcpp.runtime.ManagedPointer;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Modern Java 21+ Foreign Function & Memory (FFM) adapter.
 * Wraps java.lang.foreign.MemorySegment and Arena for safe off-heap native memory
 * manipulation, aligned memory allocations, and scoped deterministic lifetimes.
 */
public final class FFMMemoryAdapter implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment segment;
    private final long byteSize;

    public FFMMemoryAdapter(long byteSize) {
        if (byteSize <= 0) {
            throw new IllegalArgumentException("byteSize must be positive: " + byteSize);
        }
        this.arena = Arena.ofConfined();
        this.byteSize = byteSize;
        this.segment = arena.allocate(byteSize);
    }

    public FFMMemoryAdapter(long byteSize, long byteAlignment) {
        if (byteSize <= 0) {
            throw new IllegalArgumentException("byteSize must be positive: " + byteSize);
        }
        if (byteAlignment <= 0 || (byteAlignment & (byteAlignment - 1)) != 0) {
            throw new IllegalArgumentException("byteAlignment must be a positive power of two: " + byteAlignment);
        }
        this.arena = Arena.ofConfined();
        this.byteSize = byteSize;
        this.segment = arena.allocate(byteSize, byteAlignment);
    }

    public MemorySegment getSegment() {
        return segment;
    }

    public long getByteSize() {
        return byteSize;
    }

    public byte getByte(long offset) {
        return segment.get(ValueLayout.JAVA_BYTE, offset);
    }

    public void setByte(long offset, byte val) {
        segment.set(ValueLayout.JAVA_BYTE, offset, val);
    }

    public int getInt(long offset) {
        return segment.get(ValueLayout.JAVA_INT, offset);
    }

    public void setInt(long offset, int val) {
        segment.set(ValueLayout.JAVA_INT, offset, val);
    }

    public long getLong(long offset) {
        return segment.get(ValueLayout.JAVA_LONG, offset);
    }

    public void setLong(long offset, long val) {
        segment.set(ValueLayout.JAVA_LONG, offset, val);
    }

    public double getDouble(long offset) {
        return segment.get(ValueLayout.JAVA_DOUBLE, offset);
    }

    public void setDouble(long offset, double val) {
        segment.set(ValueLayout.JAVA_DOUBLE, offset, val);
    }

    public long getAddress() {
        return segment.address();
    }

    /**
     * Bridges this FFM MemorySegment to a bounds-checked ManagedPointer.
     * The resulting pointer operates directly over the native address of the segment.
     */
    public <T> ManagedPointer<T> toManagedPointer() {
        return new ManagedPointer<>(null, segment.address(), 0, byteSize);
    }

    /**
     * Bulk copy bytes into the FFM memory segment.
     */
    public void copyFrom(byte[] src, int srcOffset, long destOffset, int length) {
        if (destOffset < 0 || destOffset + length > byteSize) {
            throw new IndexOutOfBoundsException("Segment copy overflow: offset=" + destOffset + ", length=" + length + ", size=" + byteSize);
        }
        MemorySegment.copy(src, srcOffset, segment, ValueLayout.JAVA_BYTE, destOffset, length);
    }

    @Override
    public void close() {
        arena.close();
    }
}
