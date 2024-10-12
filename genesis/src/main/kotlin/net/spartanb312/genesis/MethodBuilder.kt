package net.spartanb312.genesis

import net.spartanb312.genesis.extensions.*
import org.objectweb.asm.Label
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodNode

@JvmInline
value class MethodBuilder(val methodNode: MethodNode) {

    operator fun InsnList.unaryPlus() = apply { methodNode.instructions.add(this) }
    operator fun AnnotationNode.unaryPlus() = apply { methodNode.visibleAnnotations.add(this) }
    operator fun InvisibleAnnotationNode.unaryPlus() = apply { methodNode.invisibleAnnotations.add(this) }

    @BuilderDSL
    fun MAXS(maxStack: Int, maxLocals: Int) = methodNode.visitMaxs(maxStack, maxLocals)

    @BuilderDSL
    fun INSTRUCTIONS(builder: InsnListBuilder.() -> Unit) {
        methodNode.instructions = instructions(builder)
    }

    @BuilderDSL
    fun TRYCATCH(start: Label, end: Label, handler: Label, type: String) {
        methodNode.visitTryCatchBlock(start, end, handler, type)
    }

}

@NodeDSL
inline fun MethodNode.modify(block: MethodBuilder.() -> Unit): MethodNode {
    MethodBuilder(this).block()
    return this
}

@NodeDSL
fun clinit(block: (MethodBuilder.() -> Unit)? = null): MethodNode = method(
    STATIC,
    "<clinit>",
    "()V", null,
    null,
).apply { if (block != null) modify(block) }

@NodeDSL
fun method(
    access: Modifiers,
    name: String,
    desc: String,
    signature: String? = null,
    exceptions: Array<String>? = null
): MethodNode = MethodNode(access.modifier, name, desc, signature, exceptions)

@NodeDSL
fun method(
    access: Int,
    name: String,
    desc: String,
    signature: String? = null,
    exceptions: Array<String>? = null
): MethodNode = MethodNode(access, name, desc, signature, exceptions)

@NodeDSL
inline fun method(
    access: Modifiers,
    name: String,
    desc: String,
    signature: String? = null,
    exceptions: Array<String>? = null,
    block: MethodBuilder.() -> Unit
): MethodNode = MethodNode(access.modifier, name, desc, signature, exceptions).modify(block)

@NodeDSL
inline fun method(
    access: Int,
    name: String,
    desc: String,
    signature: String? = null,
    exceptions: Array<String>? = null,
    block: MethodBuilder.() -> Unit
): MethodNode = MethodNode(access, name, desc, signature, exceptions).modify(block)