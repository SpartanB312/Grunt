package net.spartanb312.genesis.kotlin

import net.spartanb312.genesis.kotlin.extensions.BuilderDSL
import net.spartanb312.genesis.kotlin.extensions.NodeDSL
import org.objectweb.asm.tree.AnnotationNode

@JvmInline
value class AnnotationBuilder(val annotationNode: AnnotationNode) {

    operator fun set(name: String, value: Any) = annotationNode.visit(name, value)

    @BuilderDSL
    fun ENUM(name: String, desc: String, value: String) = annotationNode.visitEnum(name, desc, value)

    @BuilderDSL
    fun ANNOTATION(name: String, desc: String) = annotationNode.visitAnnotation(name, desc)

    @BuilderDSL
    fun ANNOTATION(name: String, annotation: AnnotationNode) = annotationNode.visitAnnotation(name, annotation.desc)

    @BuilderDSL
    fun ARRAY(name: String) = annotationNode.visitArray(name)

}

@NodeDSL
inline fun AnnotationNode.modify(block: AnnotationBuilder.() -> Unit): AnnotationNode {
    AnnotationBuilder(this).block()
    return this
}

@NodeDSL
fun annotation(desc: String): AnnotationNode = AnnotationNode(desc)

@NodeDSL
inline fun annotation(desc: String, block: AnnotationBuilder.() -> Unit): AnnotationNode =
    AnnotationNode(desc).modify(block)