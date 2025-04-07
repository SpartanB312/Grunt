package net.spartanb312.grunt.process.transformers.flow.process

import net.spartanb312.genesis.kotlin.extensions.INT
import net.spartanb312.genesis.kotlin.extensions.LABEL
import net.spartanb312.genesis.kotlin.extensions.getLabelNode
import net.spartanb312.genesis.kotlin.extensions.insn.GOTO
import net.spartanb312.genesis.kotlin.extensions.insn.IXOR
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.grunt.process.transformers.flow.ControlflowTransformer
import org.objectweb.asm.Label
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import kotlin.random.Random

/**
 * Feature from Krypton obfuscator
 * Author: B_312
 */
object ChaosSwitch {

    fun generate(
        targetLabel: LabelNode,
        classNode: ClassNode,
        methodNode: MethodNode,
        returnType: Type,
        conditions: Int,
        indyReobf: Boolean,
        safeLands: MutableSet<Label> = mutableSetOf(),
    ): InsnList {
        return instructions {
            val endCase = Random.nextInt()
            val startCase = endCase - conditions + 1
            val startLabel = Label()
            val labels = buildList { repeat(conditions) { add(Label()) } }
            val trueIndex = labels.indices.random()
            val trueCase = startCase + trueIndex
            val key = Random.nextInt()
            val key2 = Random.nextInt()
            val endCase2 = Random.nextInt()
            val startCase2 = endCase2 - conditions + 1
            val startLabel2 = Label()
            val labels2 = buildList { repeat(conditions) { add(Label()) } }
            safeLands.add(startLabel)
            safeLands.add(startLabel2)
            safeLands.addAll(labels)
            safeLands.addAll(labels2)
            val trashLabel = Label()
            safeLands.add(trashLabel)
            GOTO(startLabel)
            fun insertLabels(labels: List<Label>) {
                labels.forEachIndexed { index, label ->
                    LABEL(label)
                    if (index == trueIndex) +ReplaceGoto.generate(
                        targetLabel,
                        classNode,
                        methodNode,
                        returnType,
                        Random.nextBoolean(),
                        indyReobf,
                        safeLands,
                        100
                    ) else +ReplaceGoto.generate(
                        getLabelNode(safeLands.random()),
                        classNode,
                        methodNode,
                        returnType,
                        Random.nextBoolean(),
                        indyReobf,
                        safeLands,
                        100
                    )
                }
            }
            insertLabels(labels2)
            LABEL(startLabel)
            INT(trueCase xor key)
            INT(key)
            IXOR
            +TableSwitchInsnNode(
                startCase,
                endCase,
                getLabelNode(startLabel2),
                *labels.map { getLabelNode(it) }.toTypedArray()
            )
            LABEL(startLabel2)
            INT(trueCase and key2)
            INT(key2)
            IXOR
            +TableSwitchInsnNode(
                startCase2,
                endCase2,
                getLabelNode(startLabel),
                *labels2.map { getLabelNode(it) }.toTypedArray()
            )
            insertLabels(labels)
            LABEL(trashLabel)
            +JunkCode.generate(methodNode, returnType, Random.nextInt(ControlflowTransformer.maxJunkCode))
        }
    }

}