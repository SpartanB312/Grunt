package net.spartanb312.grunteon.testcase.methodrename;

import static org.junit.jupiter.api.Assertions.*;

public class OverlapInterface3To2To1 {
    public static void main(String[] args) {
        Child1 child1 = new Child1();
        assertEquals(42, child1.foo());

        Child2 child2 = new Child2();
        assertEquals(69, child2.foo());

        Child3 child3 = new Child3();
        assertEquals(100, child3.foo());

        String n1 = Father1.class.getMethods()[0].getName();
        String n2 = Father2.class.getMethods()[0].getName();
        String n3 = Father3.class.getMethods()[0].getName();
        String n4 = Child1.class.getMethods()[0].getName();
        String n5 = Child2.class.getMethods()[0].getName();
        String n6 = Child3.class.getMethods()[0].getName();
        assertTrue(n1.equals(n2) && n2.equals(n3) && n3.equals(n4) && n4.equals(n5) && n5.equals(n6), "Test failed: Method names do not match: " + String.join(", ", n1, n2, n3, n4, n5, n6));
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

    private static class Child3 extends Child1 implements Father3 {
        @Override
        public int foo() {
            return 100;
        }
    }
}
