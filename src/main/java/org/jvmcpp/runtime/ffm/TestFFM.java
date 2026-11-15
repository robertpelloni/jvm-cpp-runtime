package org.jvmcpp.runtime.ffm;

public class TestFFM {

    private static void assertEquals(Object exp, Object act, String msg) {
        if (exp == null && act == null) return;
        if (exp != null && exp.equals(act)) return;
        throw new AssertionError("TestFFM failure: " + msg + " expected=" + exp + ", actual=" + act);
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError("TestFFM failure: " + msg);
    }

    public static void main(String[] args) {
        System.out.println("=== RUNNING FFM ADAPTER TEST SUITE ===");

        // Test basic scoped allocation and read/write
        try (FFMMemoryAdapter adapter = new FFMMemoryAdapter(64)) {
            assertEquals(64L, adapter.getByteSize(), "Segment byte size");
            assertTrue(adapter.getAddress() != 0, "Native address is non-zero");

            adapter.setInt(0, 4242);
            assertEquals(4242, adapter.getInt(0), "FFM int read/write");

            adapter.setLong(8, 0x1234567890ABCDEFL);
            assertEquals(0x1234567890ABCDEFL, adapter.getLong(8), "FFM long read/write");

            adapter.setDouble(16, 3.1415926535);
            assertEquals(3.1415926535, adapter.getDouble(16), "FFM double read/write");
        }

        // Test aligned allocation
        try (FFMMemoryAdapter alignedAdapter = new FFMMemoryAdapter(64, 32)) {
            assertEquals(0L, alignedAdapter.getAddress() % 32, "Segment address is 32-byte aligned");
            alignedAdapter.setInt(0, 999);
            assertEquals(999, alignedAdapter.getInt(0), "Aligned segment read/write");
        }

        // Test ManagedPointer bridge from FFM MemorySegment
        try (FFMMemoryAdapter adapter = new FFMMemoryAdapter(128)) {
            org.jvmcpp.runtime.ManagedPointer<?> ptr = adapter.toManagedPointer();
            assertTrue(ptr.isDirect(), "toManagedPointer should be direct off-heap");
            assertEquals(128L, ptr.getBounds(), "Pointer bounds should match segment size");

            ptr.writeInt(123456);
            assertEquals(123456, ptr.readInt(), "ManagedPointer readInt over FFM segment");

            // Pointer arithmetic over FFM segment
            org.jvmcpp.runtime.ManagedPointer<?> offsetPtr = ptr.addOffset(16);
            offsetPtr.writeDouble(2.71828);
            assertEquals(2.71828, offsetPtr.readDouble(), "ManagedPointer arithmetic readDouble over FFM");

            // CString over FFM segment
            org.jvmcpp.runtime.ManagedPointer<?> strPtr = ptr.addOffset(32);
            strPtr.writeCString("Hello FFM");
            assertEquals("Hello FFM", strPtr.readCString(), "ManagedPointer CString over FFM segment");

            // Bulk copy
            byte[] rawBytes = { 10, 20, 30, 40 };
            adapter.copyFrom(rawBytes, 0, 64, rawBytes.length);
            assertEquals((byte) 10, ptr.addOffset(64).readByte(), "Bulk copy byte 0");
            assertEquals((byte) 40, ptr.addOffset(67).readByte(), "Bulk copy byte 3");
        }

        System.out.println("=== ALL FFM TESTS PASSED ===");
    }
}
