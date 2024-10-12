package net.spartanb312.genesis.java.builder;

import net.spartanb312.genesis.java.Utils;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldNode;

public abstract class FieldBuilder {

    public final FieldNode fieldNode;

    public FieldBuilder(
            int access,
            String name,
            String descriptor,
            String signature,
            Object value
    ) {
        fieldNode = new FieldNode(access, name, descriptor, signature, value);
    }

    public FieldBuilder(
            int access,
            String name,
            String descriptor,
            Object value
    ) {
        fieldNode = new FieldNode(access, name, descriptor, null, value);
    }

    public FieldBuilder(
            int access,
            String name,
            String descriptor
    ) {
        fieldNode = new FieldNode(access, name, descriptor, null, null);
    }

    public abstract void assemble();

    public FieldNode toFieldNode() {
        assemble();
        return fieldNode;
    }

    public AnnotationNode addAnnotation(AnnotationNode annotation) {
        fieldNode.visibleAnnotations = Utils.add(fieldNode.visibleAnnotations, annotation);
        return annotation;
    }

    public AnnotationNode addAnnotation(AnnotationNode annotation, boolean visible) {
        if (visible) fieldNode.visibleAnnotations = Utils.add(fieldNode.visibleAnnotations, annotation);
        else fieldNode.invisibleAnnotations = Utils.add(fieldNode.invisibleAnnotations, annotation);
        return annotation;
    }

    public AnnotationNode addAnnotation(AnnotationBuilder builder) {
        AnnotationNode annotation = builder.toAnnotationNode();
        fieldNode.visibleAnnotations = Utils.add(fieldNode.visibleAnnotations, annotation);
        return annotation;
    }

    public AnnotationNode addAnnotation(AnnotationBuilder builder, boolean visible) {
        AnnotationNode annotation = builder.toAnnotationNode();
        if (visible) fieldNode.visibleAnnotations = Utils.add(fieldNode.visibleAnnotations, annotation);
        else fieldNode.invisibleAnnotations = Utils.add(fieldNode.invisibleAnnotations, annotation);
        return annotation;
    }

}
