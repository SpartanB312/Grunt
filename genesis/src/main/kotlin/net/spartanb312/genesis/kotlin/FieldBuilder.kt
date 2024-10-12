package net.spartanb312.genesis.kotlin

import net.spartanb312.genesis.kotlin.extensions.Modifiers
import net.spartanb312.genesis.kotlin.extensions.NodeDSL
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.FieldNode

@JvmInline
value class FieldBuilder(val fieldNode: FieldNode) {
    operator fun AnnotationNode.unaryPlus() = apply {
        fieldNode.visibleAnnotations = (fieldNode.visibleAnnotations ?: mutableListOf()).also { it.add(this) }
    }
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
