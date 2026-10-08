package net.spartanb312.grunteon.testcase.fieldrename.exclusions;

public class FieldExclusions {
    public static class AParent {
        public int shadow = 11;
        public static int shared = 13;
        public long inherited = 19;
        public static long inheritedStatic = 23;
        private double hidden = 29;
        public boolean allowed = true;

        public double parentHidden() {
            return hidden;
        }
    }

    public static class BChild extends AParent {
        public int shadow = 31;
        public static int shared = 37;
        private double hidden = 41;
        public long a = 43;
        public int keep = 47;
        public int keepMore = 53;

        public double childHidden() {
            return hidden;
        }
    }

    public static class CGrandChild extends BChild {
    }

    public static class DSibling extends AParent {
    }

    public static class EReservedParent {
        public long a = 79;
    }

    public static class FReservedChild extends EReservedParent {
        public long other = 83;
    }

    public static void main(String[] args) {
        CGrandChild child = new CGrandChild();
        AParent parent = child;
        DSibling sibling = new DSibling();
        check(parent.shadow == 11 && child.shadow == 31, "instance field hiding");
        child.shadow = 59;
        parent.shadow = 61;
        check(parent.shadow == 61 && child.shadow == 59, "instance field writes");
        check(AParent.shared == 13 && CGrandChild.shared == 37, "static field hiding");
        CGrandChild.shared = 67;
        check(AParent.shared == 13 && BChild.shared == 67, "static field writes");
        check(child.inherited == 19 && sibling.inherited == 19, "inherited instance reference");
        child.inherited = 71;
        check(parent.inherited == 71 && child.a == 43, "reserved descendant name");
        check(CGrandChild.inheritedStatic == 23 && DSibling.inheritedStatic == 23, "inherited static reference");
        CGrandChild.inheritedStatic = 73;
        check(AParent.inheritedStatic == 73, "inherited static write");
        check(child.parentHidden() == 29 && child.childHidden() == 41, "private fields");
        check(child.keep == 47 && child.keepMore == 53 && child.allowed, "other fields");
        FReservedChild reserved = new FReservedChild();
        check(reserved.a == 79 && reserved.other == 83, "reserved inherited name");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
