package net.spartanb312.genesis.extensions

import net.spartanb312.genesis.InsnListBuilder
import net.spartanb312.genesis.MethodBuilder
import org.objectweb.asm.Label
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LineNumberNode

/**
 * Label/frame utils
 */
inline val Label.node get() = getLabelNode(this)

fun getLabelNode(label: Label): LabelNode {
    if (label.info !is LabelNode) {
        label.info = LabelNode()
    }
    return label.info as LabelNode
}

fun getLabelNodes(objects: Array<Any?>): Array<Any?> = objects.map {
    if (it is Label) it.node else it
}.toTypedArray()

@BuilderDSL
fun InsnListBuilder.LABEL(label: Label) = +label.node

@BuilderDSL
fun InsnListBuilder.LABEL(labelNode: LabelNode) = +labelNode

@BuilderDSL
fun InsnListBuilder.FRAME(
    type: Int,
    numLocal: Int,
    local: Array<Any?>?,
    numStack: Int,
    stack: Array<Any?>? = null
) = insnList.add(
    FrameNode(
        type,
        numLocal,
        local?.let { getLabelNodes(it) },
        numStack,
        stack?.let { getLabelNodes(it) })
)

@BuilderDSL
fun InsnListBuilder.LINE(line: Int, label: Label) = +LineNumberNode(line, label.node)

@BuilderDSL
fun MethodBuilder.LOCALVAR(
    name: String,
    descriptor: String,
    signature: String?,
    start: Label,
    end: Label,
    index: Int
) = methodNode.visitLocalVariable(name, descriptor, signature, start, end, index)