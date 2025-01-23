package net.spartanb312.grunt.process.transformers.flow.process

import net.spartanb312.genesis.kotlin.extensions.INT
import net.spartanb312.genesis.kotlin.extensions.LABEL
import net.spartanb312.genesis.kotlin.extensions.insn.GOTO
import net.spartanb312.genesis.kotlin.extensions.insn.IXOR
import net.spartanb312.genesis.kotlin.extensions.insn.LOOKUPSWITCH
import net.spartanb312.genesis.kotlin.extensions.node
import net.spartanb312.genesis.kotlin.instructions
import org.objectweb.asm.Label
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import kotlin.random.Random

/**
 * Simple protection for switch keys
 */
object LookUpSwitch {

    fun generate(pairs: List<Pair<Int, LabelNode>>, defLbl: LabelNode): InsnList {
        val keyMapping = mutableMapOf<Int, Int>()
        val newBranches = mutableListOf<Triple<Int, Label, LabelNode>>() // NewKey, NewLabel, TargetLabel
        val magic = Random.nextInt()
        pairs.forEach { (pre, target) ->
            val newKey = pre xor magic
            keyMapping[pre] = newKey
            newBranches.add(Triple(newKey, Label(), target))
        }
        newBranches.sortBy { it.first }
        return instructions {
            val entryLabel = Label()
            val newDefLabel = Label()
            GOTO(entryLabel)
            LABEL(newDefLabel)
            GOTO(defLbl)
            LABEL(entryLabel)
            INT(magic)
            IXOR
            +LookupSwitchInsnNode(
                newDefLabel.node,
                newBranches.map { it.first }.toIntArray(),
                newBranches.map { it.second.node }.toTypedArray(),
            )
            newBranches.forEach { (_, newLabel, targetLabel) ->
                LABEL(newLabel)
                GOTO(targetLabel)
            }
        }
    }

    fun generate(switchNode: LookupSwitchInsnNode): InsnList {
        val pairs = mutableListOf<Pair<Int, LabelNode>>()
        for (i in switchNode.keys.indices) {
            pairs.add(switchNode.keys[i] to switchNode.labels[i])
        }
        return generate(pairs, switchNode.dflt)
    }

    fun generate(switchNode: TableSwitchInsnNode): InsnList {
        val pairs = mutableListOf<Pair<Int, LabelNode>>()
        for ((lblIndex, key) in (switchNode.min..switchNode.max).withIndex()) {
            pairs.add(key to switchNode.labels[lblIndex])
        }
        return generate(pairs, switchNode.dflt)
    }

}