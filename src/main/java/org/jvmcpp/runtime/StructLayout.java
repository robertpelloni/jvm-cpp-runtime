package org.jvmcpp.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper to define structured memory layouts (C/C++ structs) with automatic
 * natural alignment and offset computation over ManagedPointer instances.
 */
public final class StructLayout {

    public enum Type {
        BOOLEAN(1, 1),
        BYTE(1, 1),
        CHAR(2, 2),
        SHORT(2, 2),
        INT(4, 4),
        LONG(8, 8),
        FLOAT(4, 4),
        DOUBLE(8, 8),
        POINTER(8, 8);

        public final int size;
        public final int alignment;

        Type(int size, int alignment) {
            this.size = size;
            this.alignment = alignment;
        }
    }

    public static final class Member {
        public final String name;
        public final Type type;
        public final long offset;

        Member(String name, Type type, long offset) {
            this.name = name;
            this.type = type;
            this.offset = offset;
        }
    }

    private final List<Member> members;
    private final Map<String, Member> memberMap;
    private final long totalSize;
    private final int structAlignment;

    private StructLayout(List<Member> members, Map<String, Member> memberMap, long totalSize, int structAlignment) {
        this.members = Collections.unmodifiableList(members);
        this.memberMap = Collections.unmodifiableMap(memberMap);
        this.totalSize = totalSize;
        this.structAlignment = structAlignment;
    }

    public long getSize() {
        return totalSize;
    }

    public int getAlignment() {
        return structAlignment;
    }

    public List<Member> getMembers() {
        return members;
    }

    public Member getMember(String name) {
        Member m = memberMap.get(name);
        if (m == null) {
            throw new IllegalArgumentException("Unknown struct member: " + name);
        }
        return m;
    }

    public long getOffset(String name) {
        return getMember(name).offset;
    }

    public int readInt(ManagedPointer<?> ptr, String name) {
        return ptr.addOffset(getOffset(name)).readInt();
    }

    public void writeInt(ManagedPointer<?> ptr, String name, int val) {
        ptr.addOffset(getOffset(name)).writeInt(val);
    }

    public long readLong(ManagedPointer<?> ptr, String name) {
        return ptr.addOffset(getOffset(name)).readLong();
    }

    public void writeLong(ManagedPointer<?> ptr, String name, long val) {
        ptr.addOffset(getOffset(name)).writeLong(val);
    }

    public double readDouble(ManagedPointer<?> ptr, String name) {
        return ptr.addOffset(getOffset(name)).readDouble();
    }

    public void writeDouble(ManagedPointer<?> ptr, String name, double val) {
        ptr.addOffset(getOffset(name)).writeDouble(val);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Member> members = new ArrayList<>();
        private final Map<String, Member> memberMap = new HashMap<>();
        private long currentOffset = 0;
        private int maxAlignment = 1;

        public Builder addField(String name, Type type) {
            if (memberMap.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate member name: " + name);
            }
            // Align current offset to field alignment
            int align = type.alignment;
            if (align > maxAlignment) {
                maxAlignment = align;
            }
            long remainder = currentOffset % align;
            if (remainder != 0) {
                currentOffset += (align - remainder);
            }

            Member m = new Member(name, type, currentOffset);
            members.add(m);
            memberMap.put(name, m);
            currentOffset += type.size;
            return this;
        }

        public StructLayout build() {
            // Pad total struct size to max alignment
            long remainder = currentOffset % maxAlignment;
            long finalSize = currentOffset;
            if (remainder != 0) {
                finalSize += (maxAlignment - remainder);
            }
            return new StructLayout(members, memberMap, finalSize, maxAlignment);
        }
    }
}
