package net.spartanb312.grunteon.testcase.methodrename;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class OverlapInterface3To2 {
    private static void checkFather1(Object o, int v) {
        assertEquals(v, ((Father1) o).foo());
    }

    private static void checkFather2(Object o, int v) {
        assertEquals(v, ((Father2) o).foo());
    }

    private static void checkFather3(Object o, int v) {
        assertEquals(v, ((Father3) o).foo());
    }


    public static void main(String[] args) {
        Child1 child1 = new Child1();
        assertEquals(42, child1.foo());
        Object o1 = new Random().nextLong(0, 1) < 114 ? child1 : new Object();
        checkFather1(o1, 42);
        checkFather2(o1, 42);

        Child2 child2 = new Child2();
        assertEquals(69, child2.foo());
        Object o2 = new Random().nextLong(0, 1) < 514 ? child2 : new Object();
        checkFather2(o2, 69);
        checkFather3(o2, 69);

        String n1 = Father1.class.getMethods()[0].getName();
        String n2 = Father2.class.getMethods()[0].getName();
        String n3 = Father3.class.getMethods()[0].getName();
        String n4 = Child1.class.getMethods()[0].getName();
        String n5 = Child2.class.getMethods()[0].getName();
        assertTrue(n1.equals(n2) && n2.equals(n3) && n3.equals(n4) && n4.equals(n5), "Test failed: Method names do not match: " + String.join(", ", n1, n2, n3, n4, n5));
    }

    private interface Father1 {
        int foo();
    }

    private interface Father2 {
        int foo();
    }

    private interface Father3 {
        int foo();
    }

    private static class Child1 implements Father1, Father2 {
        @Override
        public int foo() {
            return 42;
        }
    }

    private static class Child2 implements Father2, Father3 {
        @Override
        public int foo() {
            return 69;
        }
    }
}
