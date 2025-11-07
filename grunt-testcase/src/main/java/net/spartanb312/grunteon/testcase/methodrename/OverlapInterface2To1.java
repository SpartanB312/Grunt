package net.spartanb312.grunteon.testcase.methodrename;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OverlapInterface2To1 {
    public static void main(String[] args) {
        Child child = new Child();
        assertEquals(42, child.foo());

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
