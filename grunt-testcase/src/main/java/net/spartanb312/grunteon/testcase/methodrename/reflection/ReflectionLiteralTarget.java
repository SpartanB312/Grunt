package net.spartanb312.grunteon.testcase.methodrename.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static net.spartanb312.grunteon.testcase.Asserts.assertEquals;

public class ReflectionLiteralTarget {
    public String message = "field-value";

    public String hidden(int value) {
        return "method-" + value;
    }

    public static void main(String[] args) throws Throwable {
        Class<?> clazz = Class.forName(
                "net.spartanb312.grunteon.testcase.methodrename.reflection.ReflectionLiteralTarget"
        );
        Object instance = clazz.getDeclaredConstructor().newInstance();

        assertEquals("method-7", clazz.getDeclaredMethod("hidden", int.class).invoke(instance, 7));
        assertEquals("field-value", clazz.getDeclaredField("message").get(instance));

        MethodHandle handle = MethodHandles.lookup().findVirtual(
                ReflectionLiteralTarget.class,
                "hidden",
                MethodType.methodType(String.class, int.class)
        );
        assertEquals("method-8", (String) handle.invoke(instance, 8));
    }
}
