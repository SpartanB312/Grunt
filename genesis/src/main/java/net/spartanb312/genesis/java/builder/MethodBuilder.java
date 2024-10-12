package net.spartanb312.genesis.java.builder;

import net.spartanb312.genesis.java.Utils;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;

public abstract class MethodBuilder extends InsnListBuilder {

    public final MethodNode methodNode;

    public MethodBuilder(
            int access,
            String name,
            String desc,
            String signature,
            String[] exceptions
    ) {
        methodNode = new MethodNode(access, name, desc, signature, exceptions);
    }

    public MethodBuilder(
            int access,
            String name,
            String desc,
            String signature
    ) {
        methodNode = new MethodNode(access, name, desc, signature, null);
    }

    public MethodBuilder(
            int access,
            String name,
            String desc
    ) {
        methodNode = new MethodNode(access, name, desc, null, null);
    }

    public MethodNode toMethodNode() {
        methodNode.instructions = toInsnList();
        return methodNode;
    }

    public AnnotationNode addAnnotation(AnnotationNode annotation) {
        methodNode.visibleAnnotations = Utils.add(methodNode.visibleAnnotations, annotation);
        return annotation;
    }

    public AnnotationNode addAnnotation(AnnotationNode annotation, boolean visible) {
        if (visible) methodNode.visibleAnnotations = Utils.add(methodNode.visibleAnnotations, annotation);
        else methodNode.invisibleAnnotations = Utils.add(methodNode.invisibleAnnotations, annotation);
        return annotation;
    }

    public AnnotationNode addAnnotation(AnnotationBuilder builder) {
        AnnotationNode annotation = builder.toAnnotationNode();
        methodNode.visibleAnnotations = Utils.add(methodNode.visibleAnnotations, annotation);
        return annotation;
    }

    public AnnotationNode addAnnotation(AnnotationBuilder builder, boolean visible) {
        AnnotationNode annotation = builder.toAnnotationNode();
        if (visible) methodNode.visibleAnnotations = Utils.add(methodNode.visibleAnnotations, annotation);
        else methodNode.invisibleAnnotations = Utils.add(methodNode.invisibleAnnotations, annotation);
        return annotation;
    }

    public void MAXS(int maxStack, int maxLocals) {
        methodNode.visitMaxs(maxStack, maxLocals);
    }

    public void TRYCATCH(Label start, Label end, Label handler, String type) {
        methodNode.visitTryCatchBlock(start, end, handler, type);
    }

    public void LOCALVAR(String name, String descriptor, String signature, Label start, Label end, int index) {
        methodNode.visitLocalVariable(name, descriptor, signature, start, end, index);
    }

}
