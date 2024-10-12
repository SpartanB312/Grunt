package net.spartanb312.genesis

import net.spartanb312.genesis.extensions.NodeDSL
import org.objectweb.asm.Label
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList

class InsnListBuilder(val insnList: InsnList) {
    operator fun AbstractInsnNode.unaryPlus() = apply { insnList.add(this) }
    operator fun InsnList.unaryPlus() = apply { insnList.add(this) }
    val L = Labels()
}

class Labels {
    private var labels = mutableMapOf<Any, Label>()
    operator fun get(any: Any): Label = labels.getOrPut(any) { Label() }
}

@NodeDSL
inline fun InsnList.modify(block: InsnListBuilder.() -> Unit): InsnList {
    InsnListBuilder(this).block()
    return this
}

@NodeDSL
inline fun instructions(builder: InsnListBuilder.() -> Unit): InsnList = InsnList().modify(builder)