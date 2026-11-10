package org.jvmcpp.runtime;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Validates C++ object instance layout conventions, virtual dispatch,
 * single inheritance, method overriding, multiple inheritance with subobject thunking,
 * and virtual destructors.
 */
public class TestCppObject {

    private static int assertionsCount = 0;

    private static void assertTrue(boolean condition, String message) {
        assertionsCount++;
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        assertionsCount++;
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("Assertion failed: " + message + " (expected: " + expected + ", actual: " + actual + ")");
    }

    public static void main(String[] args) {
        System.out.println("=== RUNNING CPP OBJECT & VTABLE TEST SUITE ===");

        MemoryAllocator allocator = new MemoryAllocator();

        testSingleInheritanceAndVirtualDispatch(allocator);
        testVirtualDestructor(allocator);
        testMultipleInheritanceAndThunks(allocator);
        testPureVirtualFailure();
        testRTTIAndDynamicCast(allocator);

        System.out.println("=== ALL CPP OBJECT TESTS PASSED (" + assertionsCount + " assertions verified) ===");
    }

    /**
     * Validates runtime type information (RTTI) and polymorphic dynamic casting:
     * class Animal { virtual ~Animal(); }
     * class Dog : public Animal { ... }
     * class Cat : public Animal { ... }
     */
    private static void testRTTIAndDynamicCast(MemoryAllocator allocator) {
        VTable animalVt = new VTable("Animal");
        animalVt.registerMethod((thisPtr, args) -> null); // slot 0: dtor

        VTable dogVt = new VTable("Dog", animalVt);
        VTable catVt = new VTable("Cat", animalVt);

        CppObject dog = new CppObject(allocator, dogVt, 8);

        // Positive RTTI instance queries
        assertTrue(dog.isInstanceOf("Dog"), "Dog instance is Dog");
        assertTrue(dog.isInstanceOf("Animal"), "Dog instance is Animal (derived)");
        assertTrue(!dog.isInstanceOf("Cat"), "Dog instance is NOT Cat");

        // Successful dynamic_cast to base class (upcast)
        CppObject animalCast = dog.dynamicCast(animalVt);
        assertTrue(animalCast != null, "dynamic_cast<Animal*>(dog) must succeed");

        // Successful dynamic_cast to exact derived type
        CppObject dogCast = dog.dynamicCast(dogVt);
        assertTrue(dogCast != null, "dynamic_cast<Dog*>(dog) must succeed");

        // Failed dynamic_cast to unrelated type in same hierarchy (returns null)
        CppObject catCast = dog.dynamicCast(catVt);
        assertTrue(catCast == null, "dynamic_cast<Cat*>(dog) must return null");

        dog.destroy(allocator);
    }

    /**
     * Tests:
     * class Shape { virtual ~Shape(); virtual double area(); }
     * class Circle : public Shape { virtual double area() override; }
     */
    private static void testSingleInheritanceAndVirtualDispatch(MemoryAllocator allocator) {
        // Base VTable: Shape
        VTable shapeVTable = new VTable("Shape");
        // slot 0: dtor
        int dtorSlot = shapeVTable.registerMethod((thisPtr, args) -> null);
        assertEquals(0, dtorSlot, "Virtual dtor should be slot 0");

        // slot 1: area()
        int areaSlot = shapeVTable.registerMethod((thisPtr, args) -> 0.0);
        assertEquals(1, areaSlot, "area() should be slot 1");

        // Derived VTable: Circle inherits from Shape
        VTable circleVTable = new VTable("Circle", shapeVTable);
        assertEquals(2, circleVTable.getMethodCount(), "Circle should inherit 2 slots from Shape");
        assertEquals(shapeVTable, circleVTable.getParentVTable(), "Circle parent vtable should be Shape");

        // Override slot 1 (area) for Circle
        // Circle layout: offset 8 = radius (double, 8 bytes)
        circleVTable.setMethod(areaSlot, (thisPtr, args) -> {
            double radius = thisPtr.addOffset(CppObject.MEMBERS_OFFSET).readDouble();
            return Math.PI * radius * radius;
        });

        // Instantiate Circle
        CppObject circle = new CppObject(allocator, circleVTable, 8);
        circle.getMembersPointer().writeDouble(5.0);

        // Invoke virtual through base pointer convention
        Object result = circle.invokeVirtual(areaSlot);
        assertTrue(result instanceof Double, "Result should be Double");
        double area = (Double) result;
        assertTrue(Math.abs(area - (Math.PI * 25.0)) < 1e-6, "Circle area should calculate properly");

        circle.destroy(allocator);
    }

    /**
     * Tests virtual destructor invocation sequence during deletion.
     */
    private static void testVirtualDestructor(MemoryAllocator allocator) {
        AtomicBoolean derivedDtorCalled = new AtomicBoolean(false);

        VTable baseVTable = new VTable("Base");
        baseVTable.registerMethod((thisPtr, args) -> null); // slot 0: base dtor

        VTable derivedVTable = new VTable("Derived", baseVTable);
        derivedVTable.setMethod(CppObject.VIRTUAL_DTOR_SLOT, (thisPtr, args) -> {
            derivedDtorCalled.set(true);
            return null;
        });

        CppObject obj = new CppObject(allocator, derivedVTable, 16);
        assertEquals(false, derivedDtorCalled.get(), "Dtor should not be called yet");

        long activeBefore = allocator.getActiveAllocationCount();
        obj.destroy(allocator);
        assertEquals(true, derivedDtorCalled.get(), "Virtual destructor should have been dispatched on destroy()");
        assertEquals(activeBefore - 1, allocator.getActiveAllocationCount(), "Allocated memory should be freed after destroy()");
    }

    /**
     * Tests multiple inheritance layout and this-pointer adjustment (thunks):
     * class Base1 { virtual int getA(); }
     * class Base2 { virtual int getB(); }
     * class Derived : public Base1, public Base2 { ... }
     *
     * Complete object layout:
     * [0..7]: Base1 vptr
     * [8..11]: Base1 member (int a)
     * [12..15]: padding/align
     * [16..23]: Base2 vptr (secondary subobject at offset 16)
     * [24..27]: Base2 member (int b)
     * [28..31]: Derived member (int c)
     */
    private static void testMultipleInheritanceAndThunks(MemoryAllocator allocator) {
        VTable base1VTable = new VTable("Base1");
        int base1Dtor = base1VTable.registerMethod((thisPtr, args) -> null);
        int getASlot = base1VTable.registerMethod((thisPtr, args) -> {
            return thisPtr.addOffset(8).readInt();
        });

        VTable base2VTable = new VTable("Base2");
        int base2Dtor = base2VTable.registerMethod((thisPtr, args) -> null);
        int getBSlot = base2VTable.registerMethod((thisPtr, args) -> {
            // Note: In C++, getB() expects thisPtr to point to Base2 subobject or complete object.
            // If called through Base2 subobject directly without thunk, offset 8 relative to Base2 is field b.
            return thisPtr.addOffset(8).readInt();
        });

        // Derived VTable
        VTable derivedBase1VTable = new VTable("Derived_Base1", base1VTable);
        VTable derivedBase2VTable = new VTable("Derived_Base2", base2VTable);

        // Derived overrides getB in Base2 vtable to return a + b + c!
        // When getB is invoked through a Base2 subobject, the this-pointer must be thunked back to complete object!
        derivedBase2VTable.setMethod(getBSlot, (completeThis, args) -> {
            int a = completeThis.addOffset(8).readInt();
            int b = completeThis.addOffset(24).readInt();
            int c = completeThis.addOffset(28).readInt();
            return a + b + c;
        });

        long totalSize = 32;
        ManagedPointer<?> completeMemory = allocator.malloc(totalSize);

        // Write Base1 vptr at 0
        completeMemory.writePointer(derivedBase1VTable.getId());
        // Write a at 8
        completeMemory.addOffset(8).writeInt(10);

        // Write Base2 vptr at 16
        completeMemory.addOffset(16).writePointer(derivedBase2VTable.getId());
        // Write b at 24
        completeMemory.addOffset(24).writeInt(20);
        // Write c at 28
        completeMemory.addOffset(28).writeInt(30);

        CppObject derivedObj = new CppObject(completeMemory, derivedBase1VTable);
        assertEquals(10, (Integer) derivedObj.invokeVirtual(getASlot), "getA() should return 10");

        // Obtain secondary subobject view (Base2) at offset 16
        CppObject base2Subobject = derivedObj.asSubobject(16, derivedBase2VTable);
        assertEquals(-16L, base2Subobject.getThisAdjustmentOffset(), "Subobject this adjustment offset should be -16");

        // Invoke virtual method on secondary base subobject
        // Thunk should adjust 'this' back from 16 to 0, correctly reading a + b + c = 60
        Object sumResult = base2Subobject.invokeVirtual(getBSlot);
        assertEquals(60, (Integer) sumResult, "Virtual invocation through Base2 subobject should apply thunk and calculate sum");

        derivedObj.destroy(allocator);
    }

    private static void testPureVirtualFailure() {
        VTable abstractVTable = new VTable("AbstractClass");
        abstractVTable.registerMethod(null); // Pure virtual / uninitialized slot 0

        boolean caught = false;
        try {
            abstractVTable.getMethod(0);
        } catch (IllegalStateException e) {
            caught = true;
            assertTrue(e.getMessage().contains("Pure virtual method called"), "Exception should mention pure virtual");
        }
        assertTrue(caught, "Should throw IllegalStateException on pure virtual method invocation");
    }
}
