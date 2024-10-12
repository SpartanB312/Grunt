package net.spartanb312.genesis.java;

import net.spartanb312.genesis.java.builder.*;
import org.objectweb.asm.tree.*;

public class Builders {

    public static AnnotationNode annotation(AnnotationBuilder builder) {
        return builder.toAnnotationNode();
    }

    public static ClassNode clazz(ClassBuilder builder) {
        return builder.toClassNode();
    }

    public static FieldNode field(FieldBuilder builder) {
        return builder.toFieldNode();
    }

    public static MethodNode method(MethodBuilder builder) {
        return builder.toMethodNode();
    }

    public static InsnList instructions(InsnListBuilder builder) {
        return builder.toInsnList();
    }

}
