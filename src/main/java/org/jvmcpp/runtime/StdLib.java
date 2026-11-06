package org.jvmcpp.runtime;

/**
 * Standard C library shims implemented over ManagedPointer abstractions.
 * Provides C <string.h> routines with Java memory safety and bounds enforcement.
 */
public final class StdLib {

    private StdLib() {
    }

    /**
     * Calculates the length of a null-terminated C string.
     *
     * @param str Pointer to null-terminated string.
     * @return Number of characters preceding the terminating null byte.
     */
    public static long strlen(ManagedPointer<?> str) {
        if (str == null) {
            throw new IllegalArgumentException("str pointer cannot be null");
        }
        long len = 0;
        while (str.addOffset(len).readByte() != 0) {
            len++;
        }
        return len;
    }

    /**
     * Copies string src to dest (including terminating null byte).
     *
     * @param dest Destination pointer with sufficient bounds.
     * @param src Source string pointer.
     * @return dest pointer.
     */
    public static ManagedPointer<?> strcpy(ManagedPointer<?> dest, ManagedPointer<?> src) {
        if (dest == null || src == null) {
            throw new IllegalArgumentException("Pointers cannot be null for strcpy");
        }
        long idx = 0;
        while (true) {
            byte b = src.addOffset(idx).readByte();
            dest.addOffset(idx).writeByte(b);
            if (b == 0) {
                break;
            }
            idx++;
        }
        return dest;
    }

    /**
     * Copies up to count characters from string src to dest.
     * If src length is less than count, null bytes are padded.
     */
    public static ManagedPointer<?> strncpy(ManagedPointer<?> dest, ManagedPointer<?> src, long count) {
        if (dest == null || src == null) {
            throw new IllegalArgumentException("Pointers cannot be null for strncpy");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative: " + count);
        }
        long i = 0;
        boolean nullFound = false;
        while (i < count) {
            if (!nullFound) {
                byte b = src.addOffset(i).readByte();
                dest.addOffset(i).writeByte(b);
                if (b == 0) {
                    nullFound = true;
                }
            } else {
                dest.addOffset(i).writeByte((byte) 0);
            }
            i++;
        }
        return dest;
    }

    /**
     * Compares two null-terminated strings lexicographically.
     *
     * @return negative if s1 < s2, positive if s1 > s2, 0 if equal.
     */
    public static int strcmp(ManagedPointer<?> s1, ManagedPointer<?> s2) {
        if (s1 == null || s2 == null) {
            throw new IllegalArgumentException("Pointers cannot be null for strcmp");
        }
        long idx = 0;
        while (true) {
            int b1 = s1.addOffset(idx).readByte() & 0xFF;
            int b2 = s2.addOffset(idx).readByte() & 0xFF;
            if (b1 != b2) {
                return b1 - b2;
            }
            if (b1 == 0) {
                return 0;
            }
            idx++;
        }
    }

    /**
     * Compares up to count characters of two strings.
     */
    public static int strncmp(ManagedPointer<?> s1, ManagedPointer<?> s2, long count) {
        if (s1 == null || s2 == null) {
            throw new IllegalArgumentException("Pointers cannot be null for strncmp");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative: " + count);
        }
        long idx = 0;
        while (idx < count) {
            int b1 = s1.addOffset(idx).readByte() & 0xFF;
            int b2 = s2.addOffset(idx).readByte() & 0xFF;
            if (b1 != b2) {
                return b1 - b2;
            }
            if (b1 == 0) {
                return 0;
            }
            idx++;
        }
        return 0;
    }

    /**
     * Appends src to dest, overwriting the terminating null byte at dest.
     */
    public static ManagedPointer<?> strcat(ManagedPointer<?> dest, ManagedPointer<?> src) {
        if (dest == null || src == null) {
            throw new IllegalArgumentException("Pointers cannot be null for strcat");
        }
        long destLen = strlen(dest);
        strcpy(dest.addOffset(destLen), src);
        return dest;
    }

    /**
     * Formats string into destination pointer with bounds enforcement (sprintf).
     */
    public static int sprintf(ManagedPointer<?> dest, String format, Object... args) {
        if (dest == null || format == null) {
            throw new IllegalArgumentException("Destination and format string cannot be null");
        }
        String formatted = String.format(format, args);
        byte[] bytes = formatted.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        long required = bytes.length + 1; // +1 for null terminator

        if (dest.getOffset() < 0 || dest.getOffset() + required > dest.getBounds()) {
            throw new MemoryAccessException("sprintf buffer overflow: required=" + required + ", bounds=" + dest.getBounds() + ", offset=" + dest.getOffset());
        }

        for (int i = 0; i < bytes.length; i++) {
            dest.addOffset(i).writeByte(bytes[i]);
        }
        dest.addOffset(bytes.length).writeByte((byte) 0);
        return bytes.length;
    }

    // Standard C math.h routines
    public static double fabs(double x) {
        return Math.abs(x);
    }

    public static double sqrt(double x) {
        return Math.sqrt(x);
    }

    public static double pow(double base, double exp) {
        return Math.pow(base, exp);
    }

    public static double sin(double x) {
        return Math.sin(x);
    }

    public static double cos(double x) {
        return Math.cos(x);
    }

    public static double floor(double x) {
        return Math.floor(x);
    }

    public static double ceil(double x) {
        return Math.ceil(x);
    }
}
