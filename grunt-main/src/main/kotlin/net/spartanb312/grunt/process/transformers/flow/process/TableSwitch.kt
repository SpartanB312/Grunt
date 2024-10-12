package net.spartanb312.grunt.process.transformers.flow.process

import net.spartanb312.genesis.kotlin.extensions.INT
import net.spartanb312.genesis.kotlin.extensions.LABEL
import net.spartanb312.genesis.kotlin.extensions.node
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.grunt.process.transformers.flow.ControlflowTransformer
import org.objectweb.asm.Label
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import kotlin.random.Random

object TableSwitch {

    fun generate(
        targetLabel: LabelNode,
        classNode: ClassNode,
        methodNode: MethodNode,
        returnType: Type,
        conditions: Int,
        reverse: Boolean
    ): InsnList {
        return instructions {
            val endCase = Random.nextInt()
            val startCase = endCase - conditions + 1
            val startLabel = Label()
            val labels = buildList { repeat(conditions) { add(Label()) } }
            val trueIndex = labels.indices.random()
            val trueCase = startCase + trueIndex
            val key = Random.nextInt()
            LABEL(startLabel)
            INT(trueCase xor key)
            INT(key)
            +ReplaceGoto.Action.XOR.insnList
            +TableSwitchInsnNode(
                startCase,
                endCase,
                startLabel.node,
                *labels.map { it.node }.toTypedArray()
            )
            labels.forEachIndexed { index, label ->
                LABEL(label)
                if (index == trueIndex) +ReplaceGoto.generate(targetLabel, classNode, methodNode, returnType, reverse)
                else {
                    if (ControlflowTransformer.trappedCase && Random.nextInt(100) <= ControlflowTransformer.trapChance) +ReplaceGoto.generate(
                        labels.toMutableList().apply { remove(label) }.random().node,
                        classNode,
                        methodNode,
                        returnType,
                        reverse
                    ) else if (ControlflowTransformer.fakeLoop && Random.nextInt(100) <= ControlflowTransformer.loopChance) +ReplaceGoto.generate(
                        startLabel.node,
                        classNode,
                        methodNode,
                        returnType,
                        reverse
                    ) else +JunkCode.generate(
                        methodNode,
                        returnType,
                        Random.nextInt(ControlflowTransformer.maxJunkCode)
                    )
                }
            }
        }
    }

}