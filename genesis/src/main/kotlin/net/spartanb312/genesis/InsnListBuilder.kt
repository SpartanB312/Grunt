package net.spartanb312.genesis

import net.spartanb312.genesis.extensions.NodeDSL
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList

@JvmInline
value class InsnListBuilder(val insnList: InsnList) {
    operator fun AbstractInsnNode.unaryPlus() = insnList.add(this)
    operator fun InsnList.unaryPlus() = insnList.add(this)
}

@NodeDSL
inline fun InsnList.modify(block: InsnListBuilder.() -> Unit): InsnList {
    InsnListBuilder(this).block()
    return this
}

@NodeDSL
inline fun instructions(builder: InsnListBuilder.() -> Unit): InsnList = InsnList().modify(builder)