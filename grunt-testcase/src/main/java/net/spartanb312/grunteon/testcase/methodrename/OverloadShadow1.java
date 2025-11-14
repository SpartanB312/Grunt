package net.spartanb312.grunteon.testcase.methodrename;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class OverloadShadow1 {
    private static class C1 {
        public int a(int a) {
            return a + 1;
        }

        public int b(int a, int b) {
            return a + b + 11;
        }
    }

    private static class C2 extends C1 {
        public int c(int a, int b) {
            return a * b + 14;
        }

        public int d(int a, int b, int c) {
            return a * b + 14 + c;
        }
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public static void main(String[] args) {
        C1 c1 = new C1();
        assertEquals(69, c1.a(68));
        assertEquals(25, c1.b(8, 6));

        C2 c2 = new C2();
        assertEquals(69, c2.a(68));
        assertEquals(25, c2.b(8, 6));
        assertEquals(94, c2.c(1, 80));
        assertEquals(129, c2.d(20, 5,15));

        String c1n1 = C1.class.getMethods()[0].getName();
        String c1n2 = C1.class.getMethods()[1].getName();
        assertEquals(c1n1, c1n2, "Method names in Child1 not equal");

        String c2n1 = Arrays.stream(C2.class.getMethods()).filter(e -> e.getParameterCount() == 2).findAny().get().getName();
        String c2n2 = Arrays.stream(C2.class.getMethods()).filter(e -> e.getParameterCount() == 3).findAny().get().getName();
        assertNotEquals(c1n2, c2n1, "Method c(int, int) name in Child2 should not be equal to method names in Child1");
        assertEquals(c2n1, c2n2, "Method names in Child2 with different parameter counts should be equal");
    }
}
