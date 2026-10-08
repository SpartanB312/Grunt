package net.spartanb312.grunteon.testcase.classrename.keeppackage;

public class PackageAccess {
    public static void main(String[] args) throws Exception {
        PackageAccess value = new PackageAccess();
        if (a.echo(value) != value || a.number != 42) {
            throw new AssertionError("Package-private access or descriptor remapping failed");
        }
        if (Class.forName("net.spartanb312.grunteon.testcase.classrename.keeppackage.PackageAccess")
                != PackageAccess.class) {
            throw new AssertionError("Reflection literal was not remapped");
        }
    }
}

// The first Alphabet dictionary candidate must not overwrite this excluded peer.
class a {
    static int number = 42;

    static PackageAccess echo(PackageAccess value) {
        return value;
    }
}
