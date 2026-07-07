package net.spartanb312.grunteon.testcase.fieldrename.inheritedstatic;

import static net.spartanb312.grunteon.testcase.Asserts.assertTrue;

public class AbstractStaticFieldReference {
    public static void main(String[] args) {
        Base expected = new Other();
        Base.test = expected;
        Child.touch();
        assertTrue(Child.ref == expected, "Child.ref should resolve the inherited static field");
    }

    public abstract static class Base {
        public static Base test;
    }

    public static class Other extends Base {
    }

    public static class Child extends Base {
        public static Base ref;

        static {
            ref = test;
        }

        public static void touch() {
        }
    }
}
