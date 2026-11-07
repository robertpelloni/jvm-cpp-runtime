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

        System.out.println("=== BENCHMARK COMPLETE ===");
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
