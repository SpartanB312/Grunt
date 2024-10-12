package net.spartanb312.genesis.java.builder;

import org.objectweb.asm.tree.AnnotationNode;

public abstract class AnnotationBuilder {

    public final AnnotationNode annotationNode;

    public AnnotationBuilder(String desc) {
        annotationNode = new AnnotationNode(desc);
    }

    public abstract void assemble();

    public AnnotationNode toAnnotationNode() {
        assemble();
        return annotationNode;
    }

    public AnnotationBuilder addProperty(String name, Object value) {
        annotationNode.visit(name, value);
        return this;
    }

    public AnnotationBuilder addEnum(String name, String desc, String value) {
        annotationNode.visitEnum(name, desc, value);
        return this;
    }

    public AnnotationBuilder addAnnotation(String name, String desc) {
        annotationNode.visitAnnotation(name, desc);
        return this;
    }

    public AnnotationBuilder addAnnotation(String name, AnnotationNode annotation) {
        annotationNode.visitAnnotation(name, annotation.desc);
        return this;
    }

    public AnnotationBuilder addArray(String name) {
        annotationNode.visitArray(name);
        return this;
    }

    public static class Impl extends AnnotationBuilder {
        public Impl(String desc) {
            super(desc);
        }

        @Override
        public void assemble() {
        }
    }

}
