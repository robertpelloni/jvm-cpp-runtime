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

    // Standard C <stdlib.h> numeric parsing routines
    public static int atoi(ManagedPointer<?> str) {
        if (str == null) return 0;
        String s = str.readCString().trim();
        if (s.isEmpty()) return 0;
        // Parse leading digits
        int end = 0;
        if (s.charAt(0) == '-' || s.charAt(0) == '+') end++;
        while (end < s.length() && Character.isDigit(s.charAt(end))) {
            end++;
        }
        if (end == 0 || (end == 1 && (s.charAt(0) == '-' || s.charAt(0) == '+'))) return 0;
        try {
            return Integer.parseInt(s.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static long atol(ManagedPointer<?> str) {
        if (str == null) return 0L;
        String s = str.readCString().trim();
        if (s.isEmpty()) return 0L;
        int end = 0;
        if (s.charAt(0) == '-' || s.charAt(0) == '+') end++;
        while (end < s.length() && Character.isDigit(s.charAt(end))) {
            end++;
        }
        if (end == 0 || (end == 1 && (s.charAt(0) == '-' || s.charAt(0) == '+'))) return 0L;
        try {
            return Long.parseLong(s.substring(0, end));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static double atof(ManagedPointer<?> str) {
        if (str == null) return 0.0;
        String s = str.readCString().trim();
        if (s.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // Standard C pseudo-random routines
    private static long nextRand = 1;

    public static void srand(int seed) {
        nextRand = seed & 0xFFFFFFFFL;
    }

    public static int rand() {
        nextRand = (nextRand * 1103515245L + 12345L) & 0x7FFFFFFFL;
        return (int) (nextRand / 65536L) % 32768;
    }

    @FunctionalInterface
    public interface CmpFunc {
        int compare(ManagedPointer<?> a, ManagedPointer<?> b);
    }

    /**
     * In-place quicksort over memory block (C qsort).
     */
    public static void qsort(ManagedPointer<?> base, int nmemb, int size, CmpFunc cmp) {
        if (base == null || nmemb <= 1 || size <= 0 || cmp == null) {
            return;
        }
        quickSortHelper(base, 0, nmemb - 1, size, cmp);
    }

    private static void quickSortHelper(ManagedPointer<?> base, int low, int high, int size, CmpFunc cmp) {
        if (low < high) {
            int pi = partition(base, low, high, size, cmp);
            quickSortHelper(base, low, pi - 1, size, cmp);
            quickSortHelper(base, pi + 1, high, size, cmp);
        }
    }

    private static int partition(ManagedPointer<?> base, int low, int high, int size, CmpFunc cmp) {
        ManagedPointer<?> pivot = base.addOffset((long) high * size);
        int i = low - 1;
        byte[] temp = new byte[size];

        for (int j = low; j < high; j++) {
            ManagedPointer<?> curr = base.addOffset((long) j * size);
            if (cmp.compare(curr, pivot) <= 0) {
                i++;
                if (i != j) {
                    swapElements(base.addOffset((long) i * size), curr, temp, size);
                }
            }
        }
        swapElements(base.addOffset((long) (i + 1) * size), pivot, temp, size);
        return i + 1;
    }

    private static void swapElements(ManagedPointer<?> a, ManagedPointer<?> b, byte[] temp, int size) {
        for (int k = 0; k < size; k++) {
            temp[k] = a.addOffset(k).readByte();
        }
        for (int k = 0; k < size; k++) {
            a.addOffset(k).writeByte(b.addOffset(k).readByte());
        }
        for (int k = 0; k < size; k++) {
            b.addOffset(k).writeByte(temp[k]);
        }
    }

    /**
     * Binary search over sorted memory block (C bsearch).
     */
    public static ManagedPointer<?> bsearch(ManagedPointer<?> key, ManagedPointer<?> base, int nmemb, int size, CmpFunc cmp) {
        if (key == null || base == null || nmemb <= 0 || size <= 0 || cmp == null) {
            return null;
        }
        int low = 0;
        int high = nmemb - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            ManagedPointer<?> midPtr = base.addOffset((long) mid * size);
            int c = cmp.compare(key, midPtr);

            if (c < 0) {
                high = mid - 1;
            } else if (c > 0) {
                low = mid + 1;
            } else {
                return midPtr;
            }
        }
        return null;
    }
}
