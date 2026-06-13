package net.spartanb312.grunteon.testcase.methodrename.reflection;

import static net.spartanb312.grunteon.testcase.Asserts.assertEquals;

public class ReflectionControlFlowTarget {
    public static void main(String[] args) throws Exception {
        assertEquals("left-method", invoke(true));
        assertEquals("right-method", invoke(false));
        assertEquals("left-field", read(true));
        assertEquals("right-field", read(false));
    }

    private static String invoke(boolean left) throws Exception {
        Class<?> owner;
        Object instance;
        String name;
        if (left) {
            owner = LeftOwner.class;
            instance = new LeftOwner();
            name = "leftAction";
        } else {
            owner = RightOwner.class;
            instance = new RightOwner();
            name = "rightAction";
        }
        return (String) owner.getDeclaredMethod(name).invoke(instance);
    }

    private static String read(boolean left) throws Exception {
        Class<?> owner;
        Object instance;
        String name;
        if (left) {
            owner = LeftOwner.class;
            instance = new LeftOwner();
            name = "leftValue";
        } else {
            owner = RightOwner.class;
            instance = new RightOwner();
            name = "rightValue";
        }
        return (String) owner.getDeclaredField(name).get(instance);
    }

    public static class LeftOwner {
        public String leftValue = "left-field";

        public String padding() {
            return "padding";
        }

        public String leftAction() {
            return "left-method";
        }
    }

    public static class RightOwner {
        public String rightValue = "right-field";

        public String rightAction() {
            return "right-method";
        }
    }
}
