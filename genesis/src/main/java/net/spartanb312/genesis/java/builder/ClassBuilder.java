package net.spartanb312.genesis.java.builder;

import net.spartanb312.genesis.java.Builders;
import net.spartanb312.genesis.java.Utils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public abstract class ClassBuilder {

    public final ClassNode classNode;

    public ClassBuilder(
            int version,
            int access,
            String name,
            String signature,
            String superName,
            String[] interfaces
    ) {
        ClassNode created = new ClassNode();
        created.visit(version, access, name, signature, superName, interfaces);
        classNode = created;
    }

    public ClassBuilder(
            int access,
            String name,
            String signature,
            String superName,
            String[] interfaces
    ) {
        ClassNode created = new ClassNode();
        created.visit(Opcodes.V1_7, access, name, signature, superName, interfaces);
        classNode = created;
    }

    public ClassBuilder(
            int access,
            String name,
            String signature,
            String superName
    ) {
        ClassNode created = new ClassNode();
        created.visit(Opcodes.V1_7, access, name, signature, superName, null);
        classNode = created;
    }

    public ClassBuilder(
            int access,
            String name,
            String superName
    ) {
        ClassNode created = new ClassNode();
        created.visit(Opcodes.V1_7, access, name, null, superName, null);
        classNode = created;
    }

    public ClassBuilder(
            int access,
            String name
    ) {
        ClassNode created = new ClassNode();
        created.visit(Opcodes.V1_7, access, name, null, "java/lang/Object", null);
        classNode = created;
    }

    public abstract void assemble();

    public ClassNode toClassNode() {
        assemble();
        return classNode;
    }

    public AnnotationNode addAnnotation(AnnotationNode annotation) {
        classNode.visibleAnnotations = Utils.add(classNode.visibleAnnotations, annotation);
        return annotation;
    }

    public AnnotationNode addAnnotation(AnnotationNode annotation, boolean visible) {
        if (visible) classNode.visibleAnnotations = Utils.add(classNode.visibleAnnotations, annotation);
        else classNode.invisibleAnnotations = Utils.add(classNode.invisibleAnnotations, annotation);
        return annotation;
    }

    public AnnotationNode addAnnotation(AnnotationBuilder builder) {
        AnnotationNode annotation = builder.toAnnotationNode();
        classNode.visibleAnnotations = Utils.add(classNode.visibleAnnotations, annotation);
        return annotation;
    }

    public AnnotationNode addAnnotation(AnnotationBuilder builder, boolean visible) {
        AnnotationNode annotation = builder.toAnnotationNode();
        if (visible) classNode.visibleAnnotations = Utils.add(classNode.visibleAnnotations, annotation);
        else classNode.invisibleAnnotations = Utils.add(classNode.invisibleAnnotations, annotation);
        return annotation;
    }

    public MethodNode addMethod(MethodNode method) {
        classNode.methods.add(method);
        return method;
    }

    public MethodNode addMethod(MethodBuilder builder) {
        MethodNode methodNode = Builders.method(builder);
        classNode.methods.add(methodNode);
        return methodNode;
    }

    public FieldNode addField(FieldNode field) {
        classNode.fields.add(field);
        return field;
    }

    public FieldNode addField(FieldBuilder builder) {
        FieldNode fieldNode = Builders.field(builder);
        classNode.fields.add(fieldNode);
        return fieldNode;
    }

}
