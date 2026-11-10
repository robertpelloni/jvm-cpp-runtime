package org.jvmcpp.runtime;

public class BenchmarkRuntime {

    private static final int WARMUP_ITERS = 10_000;
    private static final int BENCHMARK_ITERS = 100_000;

    public static void main(String[] args) {
        System.out.println("=== JVM-CPP-RUNTIME BENCHMARK SUITE ===");
        MemoryAllocator allocator = new MemoryAllocator();

        // 1. Primitive Read/Write Throughput
        benchPrimitiveAccess(allocator);

        // 2. Memory Allocation & Pooling Throughput
        benchAllocations(allocator);

        // 3. String & Memcpy Performance
        benchMemoryOps(allocator);

        // 4. C++ Virtual Method Dispatch Performance
        benchVirtualDispatch(allocator);

        // 5. Standard C Library Routines Performance
        benchStdLibOps(allocator);

        // 6. Structured Memory Layout Field Operations
        benchStructLayoutAccess(allocator);

        // 7. Off-Heap Direct Memory Read/Write Operations
        benchDirectNativeMemoryAccess(allocator);

        System.out.println("=== BENCHMARK COMPLETE ===");
    }

    private static void benchVirtualDispatch(MemoryAllocator allocator) {
        VTable vt = new VTable("BenchDerived");
        vt.registerMethod((thisPtr, args) -> null); // slot 0: dtor
        int computeSlot = vt.registerMethod((thisPtr, args) -> {
            int val = thisPtr.addOffset(CppObject.MEMBERS_OFFSET).readInt();
            return val * 2 + 1;
        });

        CppObject obj = new CppObject(allocator, vt, 4);
        obj.getMembersPointer().writeInt(10);

        // Warmup
        for (int i = 0; i < WARMUP_ITERS; i++) {
            obj.invokeVirtual(computeSlot);
        }

        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            obj.invokeVirtual(computeSlot);
        }
        long duration = System.nanoTime() - start;
        double opsPerSec = (double) BENCHMARK_ITERS / (duration / 1_000_000_000.0);
        System.out.printf("[Benchmark] Virtual method dispatch ops/sec: %,.0f (avg: %.2f ns/call)%n",
                opsPerSec, (double) duration / BENCHMARK_ITERS);

        obj.destroy(allocator);
    }

    private static void benchStdLibOps(MemoryAllocator allocator) {
        // String formatting benchmark
        ManagedPointer<?> buffer = allocator.malloc(128);
        String fmt = "Item #%d: value=%.2f, status=%s";

        for (int i = 0; i < WARMUP_ITERS; i++) {
            StdLib.sprintf(buffer, fmt, i, 42.5, "OK");
        }

        long startSprintf = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            StdLib.sprintf(buffer, fmt, i, 42.5, "OK");
        }
        long durationSprintf = System.nanoTime() - startSprintf;
        double sprintfOpsSec = (double) BENCHMARK_ITERS / (durationSprintf / 1_000_000_000.0);
        System.out.printf("[Benchmark] StdLib.sprintf ops/sec:           %,.0f (avg: %.2f ns/call)%n",
                sprintfOpsSec, (double) durationSprintf / BENCHMARK_ITERS);

        allocator.free(buffer);

        // In-place qsort benchmark (100 elements)
        int arrayLen = 100;
        int sortIterations = 2_000;
        ManagedPointer<?> sortArray = allocator.malloc(arrayLen * 4);

        long startSort = System.nanoTime();
        for (int it = 0; it < sortIterations; it++) {
            for (int k = 0; k < arrayLen; k++) {
                sortArray.addOffset(k * 4).writeInt((arrayLen - k) * 7);
            }
            StdLib.qsort(sortArray, arrayLen, 4, (p1, p2) -> Integer.compare(p1.readInt(), p2.readInt()));
        }
        long durationSort = System.nanoTime() - startSort;
        System.out.printf("[Benchmark] StdLib.qsort (100 elements) ops/sec: %,.0f%n",
                (double) sortIterations / (durationSort / 1_000_000_000.0));

        allocator.free(sortArray);
    }

    private static void benchPrimitiveAccess(MemoryAllocator allocator) {
        ManagedPointer<?> ptr = allocator.malloc(64);

        // Warmup
        for (int i = 0; i < WARMUP_ITERS; i++) {
            ptr.writeInt(i);
            int v = ptr.readInt();
        }

        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            ptr.writeInt(i);
            int v = ptr.readInt();
        }
        long durationNs = System.nanoTime() - start;
        double opsPerSec = (BENCHMARK_ITERS * 2.0) / (durationNs / 1_000_000_000.0);
        System.out.printf("[Benchmark] Primitive read/write ops/sec: %,.0f (avg: %.2f ns/op)%n", opsPerSec, (double) durationNs / (BENCHMARK_ITERS * 2));

        allocator.free(ptr);
    }

    private static void benchStructLayoutAccess(MemoryAllocator allocator) {
        StructLayout layout = StructLayout.builder()
                .addField("tag", StructLayout.Type.BYTE)
                .addField("id", StructLayout.Type.INT)
                .addField("val", StructLayout.Type.DOUBLE)
                .build();

        ManagedPointer<?> mem = allocator.malloc(layout.getSize());

        for (int i = 0; i < WARMUP_ITERS; i++) {
            layout.writeInt(mem, "id", i);
            layout.writeDouble(mem, "val", i * 1.5);
            int id = layout.readInt(mem, "id");
            double val = layout.readDouble(mem, "val");
        }

        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            layout.writeInt(mem, "id", i);
            layout.writeDouble(mem, "val", i * 1.5);
            int id = layout.readInt(mem, "id");
            double val = layout.readDouble(mem, "val");
        }
        long duration = System.nanoTime() - start;
        double opsPerSec = (BENCHMARK_ITERS * 4.0) / (duration / 1_000_000_000.0);
        System.out.printf("[Benchmark] StructLayout field ops/sec:        %,.0f (avg: %.2f ns/op)%n",
                opsPerSec, (double) duration / (BENCHMARK_ITERS * 4));

        allocator.free(mem);
    }

    private static void benchDirectNativeMemoryAccess(MemoryAllocator allocator) {
        ManagedPointer<?> direct = allocator.mallocDirect(64);

        for (int i = 0; i < WARMUP_ITERS; i++) {
            direct.writeLong(i * 1000L);
            long val = direct.readLong();
        }

        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            direct.writeLong(i * 1000L);
            long val = direct.readLong();
        }
        long duration = System.nanoTime() - start;
        double opsPerSec = (BENCHMARK_ITERS * 2.0) / (duration / 1_000_000_000.0);
        System.out.printf("[Benchmark] Off-heap direct read/write ops/sec:%,.0f (avg: %.2f ns/op)%n",
                opsPerSec, (double) duration / (BENCHMARK_ITERS * 2));

        allocator.free(direct);
    }

    private static void benchAllocations(MemoryAllocator allocator) {
        // Standard malloc/free
        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            ManagedPointer<?> p = allocator.malloc(32);
            allocator.free(p);
        }
        long durationMalloc = System.nanoTime() - start;
        System.out.printf("[Benchmark] Standard malloc/free ops/sec: %,.0f (avg: %.2f ns/op)%n",
                (double) BENCHMARK_ITERS / (durationMalloc / 1_000_000_000.0), (double) durationMalloc / BENCHMARK_ITERS);

        // Pooled allocate/free
        start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            ManagedPointer<?> p = allocator.allocatePooled(32);
            allocator.freePooled(p);
        }
        long durationPooled = System.nanoTime() - start;
        System.out.printf("[Benchmark] Pooled alloc/free ops/sec:   %,.0f (avg: %.2f ns/op)%n",
                (double) BENCHMARK_ITERS / (durationPooled / 1_000_000_000.0), (double) durationPooled / BENCHMARK_ITERS);
    }

    private static void benchMemoryOps(MemoryAllocator allocator) {
        ManagedPointer<?> src = allocator.malloc(256);
        ManagedPointer<?> dst = allocator.malloc(256);
        src.fillMemory((byte) 0xEE, 256);

        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            MemoryAllocator.copyMemory(dst, src, 256);
        }
        long duration = System.nanoTime() - start;
        double throughputMBps = (double) (BENCHMARK_ITERS * 256L) / (1024 * 1024) / (duration / 1_000_000_000.0);
        System.out.printf("[Benchmark] copyMemory (memcpy) throughput: %,.2f MB/sec%n", throughputMBps);

        allocator.free(src);
        allocator.free(dst);
    }
}
