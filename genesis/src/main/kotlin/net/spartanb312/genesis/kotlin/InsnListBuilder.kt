package net.spartanb312.genesis.kotlin

import net.spartanb312.genesis.kotlin.extensions.NodeDSL
import org.objectweb.asm.Label
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList

class InsnListBuilder(val insnList: InsnList = InsnList()) {
    private var labels = mutableMapOf<Any, Label>()
    val L = this
    operator fun AbstractInsnNode.unaryPlus() = apply { insnList.add(this) }
    operator fun InsnList.unaryPlus() = apply { insnList.add(this) }
    operator fun get(any: Any): Label = labels.getOrPut(any) { Label() }
    infix fun L(any: Any): Label = labels.getOrPut(any) { Label() }
}

@NodeDSL
inline fun InsnList.modify(block: InsnListBuilder.() -> Unit): InsnList {
    InsnListBuilder(this).block()
    return this
}

@NodeDSL
inline fun instructions(builder: InsnListBuilder.() -> Unit): InsnList = InsnList().modify(builder)