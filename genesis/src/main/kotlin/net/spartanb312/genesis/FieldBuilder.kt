package net.spartanb312.genesis

import net.spartanb312.genesis.extensions.BuilderDSL
import net.spartanb312.genesis.extensions.Modifiers
import net.spartanb312.genesis.extensions.NodeDSL
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.FieldNode

@JvmInline
value class FieldBuilder(val fieldNode: FieldNode) {

    @BuilderDSL
    operator fun AnnotationNode.unaryPlus() = fieldNode.visibleAnnotations.add(this)

    @BuilderDSL
    operator fun InvisibleAnnotationNode.unaryPlus() = fieldNode.invisibleAnnotations.add(this)

}

@NodeDSL
inline fun FieldNode.modify(block: FieldBuilder.() -> Unit): FieldNode {
    FieldBuilder(this).block()
    return this
}

@NodeDSL
fun field(
    access: Modifiers,
    name: String,
    desc: String,
    signature: String? = null,
    value: Any? = null
): FieldNode = FieldNode(access.modifier, name, desc, signature, value)

@NodeDSL
fun field(
    access: Int,
    name: String,
    desc: String,
    signature: String? = null,
    value: Any? = null
): FieldNode = FieldNode(access, name, desc, signature, value)


@NodeDSL
inline fun field(
    access: Modifiers,
    name: String,
    desc: String,
    signature: String? = null,
    value: Any? = null,
    block: FieldBuilder.() -> Unit
): FieldNode = FieldNode(access.modifier, name, desc, signature, value).modify(block)

@NodeDSL
inline fun field(
    access: Int,
    name: String,
    desc: String,
    signature: String? = null,
    value: Any? = null,
    block: FieldBuilder.() -> Unit
): FieldNode = FieldNode(access, name, desc, signature, value).modify(block)
