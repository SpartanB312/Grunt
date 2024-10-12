package net.spartanb312.genesis.java;

import org.objectweb.asm.Opcodes;

public class Access {

    /**
     * Access constants
     */
    public static final int PUBLIC = Opcodes.ACC_PUBLIC;
    public static final int PRIVATE = Opcodes.ACC_PRIVATE;
    public static final int PROTECTED = Opcodes.ACC_PROTECTED;
    public static final int STATIC = Opcodes.ACC_STATIC;
    public static final int FINAL = Opcodes.ACC_FINAL;
    public static final int SUPER = Opcodes.ACC_SUPER;
    public static final int SYNCHRONIZE = Opcodes.ACC_SYNCHRONIZED;
    public static final int OPEN = Opcodes.ACC_OPEN;
    public static final int TRANSITIVE = Opcodes.ACC_TRANSITIVE;
    public static final int NATIVE = Opcodes.ACC_NATIVE;
    public static final int INTERFACE = Opcodes.ACC_INTERFACE;
    public static final int ABSTRACT = Opcodes.ACC_ABSTRACT;
    public static final int STRICT = Opcodes.ACC_STRICT;
    public static final int SYNTHETIC = Opcodes.ACC_SYNTHETIC;
    public static final int ANNOTATION = Opcodes.ACC_ANNOTATION;
    public static final int ENUM = Opcodes.ACC_ENUM;
    public static final int MANDATED = Opcodes.ACC_MANDATED;
    public static final int MODULE = Opcodes.ACC_MODULE;
    public static final int RECORD = Opcodes.ACC_RECORD;
    public static final int DEPRECATED = Opcodes.ACC_DEPRECATED;

    /**
     * Access utils
     */
    public static boolean equals(int current, int other) {
        return (current & other) == other;
    }

    public static boolean intersects(int current, int other) {
        return (current & other) != 0;
    }

    public static boolean isPublic(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_PUBLIC);
    }

    public static boolean isPrivate(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_PRIVATE);
    }

    public static boolean isProtected(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_PROTECTED);
    }

    public static boolean isStatic(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_STATIC);
    }

    public static boolean isFinal(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_FINAL);
    }

    public static boolean isSuper(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_SUPER);
    }

    public static boolean isSynchronized(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_SYNCHRONIZED);
    }

    public static boolean isOpen(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_OPEN);
    }

    public static boolean isTransitive(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_TRANSITIVE);
    }

    public static boolean isNative(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_NATIVE);
    }

    public static boolean isInterface(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_INTERFACE);
    }

    public static boolean isAbstract(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_ABSTRACT);
    }

    public static boolean isStrict(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_STRICT);
    }

    public static boolean isSynthetic(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_SYNTHETIC);
    }

    public static boolean isAnnotation(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_ANNOTATION);
    }

    public static boolean isEnum(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_ENUM);
    }

    public static boolean isMandated(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_MANDATED);
    }

    public static boolean isModule(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_MODULE);
    }

    public static boolean isRecord(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_RECORD);
    }

    public static boolean isDeprecated(int modifiers) {
        return intersects(modifiers, Opcodes.ACC_DEPRECATED);
    }

}
