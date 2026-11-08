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

        System.out.println("=== ALL FFM TESTS PASSED ===");
    }
}
