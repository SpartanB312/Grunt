package net.spartanb312.grunteon.testcase.methodrename;

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

    public static void main(String[] args) {
        C1 c1 = new C1();
        assertEquals(69, c1.a(68));
        assertEquals(25, c1.b(8, 6));

        C2 c2 = new C2();
        assertEquals(94, c2.c(1, 80));
        assertEquals(129, c2.d(20, 5,15));
    }
}
