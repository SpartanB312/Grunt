package net.spartanb312.grunteon.testcase.methodrename;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class OverlapInterface2To1 {
    private static void checkFather1(Object o) {
        assertEquals(42, ((Father1) o).foo());
    }

    private static void checkFather2(Object o) {
        assertEquals(42, ((Father2) o).foo());
    }


    public static void main(String[] args) {
        Child child = new Child();
        assertEquals(42, child.foo());
        Object o = new Random().nextLong(0, 1) < 114 ? child : new Object();

        checkFather1(o);
        checkFather2(o);

        String n1 = Father1.class.getMethods()[0].getName();
        String n2 = Father2.class.getMethods()[0].getName();
        String n3 = Child.class.getMethods()[0].getName();
        assertTrue(n1.equals(n2) && n2.equals(n3), "Test failed: Method names do not match: " + String.join(", ", n1, n2, n3));
    }

    private interface Father1 {
        int foo();
    }

    private interface Father2 {
        int foo();
    }

    private static class Child implements Father1, Father2 {
        @Override
        public int foo() {
            return 42;
        }
    }
}
