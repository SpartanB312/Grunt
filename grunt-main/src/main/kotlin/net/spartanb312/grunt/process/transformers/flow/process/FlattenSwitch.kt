package net.spartanb312.grunt.process.transformers.flow.process

import net.spartanb312.genesis.kotlin.extensions.INT
import net.spartanb312.genesis.kotlin.extensions.insn.GOTO
import net.spartanb312.genesis.kotlin.extensions.insn.IF_ICMPEQ
import net.spartanb312.genesis.kotlin.extensions.insn.ILOAD
import net.spartanb312.genesis.kotlin.extensions.insn.ISTORE
import net.spartanb312.genesis.kotlin.instructions
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode

object FlattenSwitch {

    fun generate(
        insnNode: AbstractInsnNode,
        slot: Int
    ): InsnList {
        return instructions {
            ISTORE(slot)

            when (insnNode) {
                is TableSwitchInsnNode -> {
                    for ((index, key) in (insnNode.min..insnNode.max).withIndex()) {
                        ILOAD(slot)
                        INT(key)
                        IF_ICMPEQ(insnNode.labels[index])
                    }
                    GOTO(insnNode.dflt)
                }
                is LookupSwitchInsnNode -> {
                    for (keyIndex in 0 until insnNode.keys.size) {
                        ILOAD(slot)
                        INT(keyIndex)
                        IF_ICMPEQ(insnNode.labels[keyIndex])
                    }
                    GOTO(insnNode.dflt)
                }
            }
        }
    }
}