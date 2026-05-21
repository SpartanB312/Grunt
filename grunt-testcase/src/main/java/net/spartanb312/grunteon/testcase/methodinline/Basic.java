package net.spartanb312.grunteon.testcase.methodinline;

import net.spartanb312.grunteon.testcase.Asserts;

public class Basic {

    private final int seed;

    public Basic(int seed) {
        this.seed = seed;
    }

    public int run(int value) {
        return add(value) + multiply(value, 3) + finalAdd(value);
    }

    public static int callNoThis(Basic instance) {
        return instance.noThis();
    }

    private int add(int value) {
        return seed + value;
    }

    private static int multiply(int left, int right) {
        return left * right;
    }

    public final int finalAdd(int value) {
        return seed + value + 1;
    }

    private int noThis() {
        return 9;
    }

    public static void main(String[] args) {
        Basic basic = new Basic(7);
        Asserts.assertEquals(40, basic.run(5));

        try {
            callNoThis(null);
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException ignored) {
        }
    }
}
