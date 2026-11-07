package org.jvmcpp.runtime;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * End-to-end integration tests simulating realistic C++ runtime scenarios
 * compiled to JVM bytecode targeting ManagedPointer and MemoryAllocator.
 */
public class IntegrationTest {

    private static int assertions = 0;

    private static void assertTrue(boolean cond, String msg) {
        assertions++;
        if (!cond) throw new AssertionError("Integration failure: " + msg);
    }

    private static void assertEquals(Object exp, Object act, String msg) {
        assertions++;
        if (exp == null && act == null) return;
        if (exp != null && exp.equals(act)) return;
        throw new AssertionError("Integration failure: " + msg + " expected=" + exp + ", actual=" + act);
    }

    public static void main(String[] args) {
        System.out.println("=== RUNNING RUNTIME INTEGRATION TEST SUITE ===");
        MemoryAllocator allocator = new MemoryAllocator();

        testDynamicVectorGrowth(allocator);
        testStructArrayTraversal(allocator);
        testPolymorphicCppObjectHierarchy(allocator);
        testConcurrentProducerConsumerPooling(allocator);

        System.out.println("=== ALL INTEGRATION TESTS PASSED (" + assertions + " assertions verified) ===");
    }

    /**
     * Simulates std::vector<int> dynamic expansion using malloc, realloc, and pointer indexing.
     */
    private static void testDynamicVectorGrowth(MemoryAllocator allocator) {
        long capacity = 4; // 4 ints = 16 bytes
        long size = 0;
        ManagedPointer<?> buffer = allocator.malloc(capacity * 4);

        for (int i = 0; i < 50; i++) {
            if (size >= capacity) {
                capacity *= 2;
                buffer = allocator.realloc(buffer, capacity * 4);
            }
            buffer.addOffset(size * 4).writeInt(i * 10);
            size++;
        }

        assertEquals(50L, size, "Vector size mismatch");
        assertTrue(capacity >= 50, "Vector capacity mismatch");

        for (int i = 0; i < 50; i++) {
            int val = buffer.addOffset(i * 4).readInt();
            assertEquals(i * 10, val, "Vector element mismatch at " + i);
        }

        allocator.free(buffer);
    }

    /**
     * Simulates an array of C++ structs traversed via pointer arithmetic.
     * struct Particle { float x, y, z; int id; }; // size = 16 bytes
     */
    private static void testStructArrayTraversal(MemoryAllocator allocator) {
        StructLayout particleLayout = StructLayout.builder()
                .addField("x", StructLayout.Type.FLOAT)
                .addField("y", StructLayout.Type.FLOAT)
                .addField("z", StructLayout.Type.FLOAT)
                .addField("id", StructLayout.Type.INT)
                .build();

        assertEquals(16L, particleLayout.getSize(), "Particle struct size mismatch");

        int count = 10;
        ManagedPointer<?> arrayBlock = allocator.malloc(count * particleLayout.getSize());

        // Initialize array elements using pointer arithmetic
        for (int i = 0; i < count; i++) {
            ManagedPointer<?> elementPtr = arrayBlock.addOffset(i * particleLayout.getSize());
            elementPtr.addOffset(particleLayout.getOffset("x")).writeFloat(1.0f * i);
            elementPtr.addOffset(particleLayout.getOffset("y")).writeFloat(2.0f * i);
            elementPtr.addOffset(particleLayout.getOffset("z")).writeFloat(3.0f * i);
            particleLayout.writeInt(elementPtr, "id", 100 + i);
        }

        // Verify elements
        for (int i = 0; i < count; i++) {
            ManagedPointer<?> elementPtr = arrayBlock.addOffset(i * particleLayout.getSize());
            assertEquals(1.0f * i, elementPtr.addOffset(particleLayout.getOffset("x")).readFloat(), "Particle x");
            assertEquals(2.0f * i, elementPtr.addOffset(particleLayout.getOffset("y")).readFloat(), "Particle y");
            assertEquals(3.0f * i, elementPtr.addOffset(particleLayout.getOffset("z")).readFloat(), "Particle z");
            assertEquals(100 + i, particleLayout.readInt(elementPtr, "id"), "Particle id");
        }

        allocator.free(arrayBlock);
    }

    /**
     * Simulates full C++ polymorphic hierarchy:
     * Base: Animal (virtual sound(), virtual legs())
     * Derived1: Dog
     * Derived2: Duck
     */
    private static void testPolymorphicCppObjectHierarchy(MemoryAllocator allocator) {
        VTable animalVt = new VTable("Animal");
        int soundSlot = animalVt.registerMethod((thisPtr, args) -> "generic");
        int legsSlot = animalVt.registerMethod((thisPtr, args) -> 0);

        VTable dogVt = new VTable("Dog");
        dogVt.setMethod(soundSlot, (thisPtr, args) -> "woof");
        dogVt.setMethod(legsSlot, (thisPtr, args) -> 4);

        VTable duckVt = new VTable("Duck");
        duckVt.setMethod(soundSlot, (thisPtr, args) -> "quack");
        duckVt.setMethod(legsSlot, (thisPtr, args) -> 2);

        CppObject dog = new CppObject(allocator, dogVt, 0);
        CppObject duck = new CppObject(allocator, duckVt, 0);

        assertEquals("woof", dog.invokeVirtual(soundSlot), "Dog sound");
        assertEquals(4, dog.invokeVirtual(legsSlot), "Dog legs");

        assertEquals("quack", duck.invokeVirtual(soundSlot), "Duck sound");
        assertEquals(2, duck.invokeVirtual(legsSlot), "Duck legs");

        dog.destroy(allocator);
        duck.destroy(allocator);
    }

    /**
     * Simulates multithreaded producer-consumer memory allocation and pooling stress.
     */
    private static void testConcurrentProducerConsumerPooling(MemoryAllocator allocator) {
        int threads = 4;
        int operationsPerThread = 200;
        Thread[] pool = new Thread[threads];
        final AtomicBoolean failed = new AtomicBoolean(false);
        final AtomicInteger completed = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int tid = i;
            pool[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        ManagedPointer<?> p = allocator.allocatePooled(64);
                        p.writeLong(tid * 1_000_000L + j);
                        Thread.yield();
                        assertEquals(tid * 1_000_000L + j, p.readLong(), "Pooled multithreaded read mismatch");
                        allocator.freePooled(p);
                    }
                    completed.incrementAndGet();
                } catch (Throwable t) {
                    failed.set(true);
                    t.printStackTrace();
                }
            });
            pool[i].start();
        }

        for (Thread t : pool) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        assertTrue(!failed.get(), "Concurrent pooling thread failure detected");
        assertEquals(threads, completed.get(), "All producer threads completed successfully");
    }
}
