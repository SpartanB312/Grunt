package net.spartanb312.grunt.process.transformers.flow.process

import net.spartanb312.grunt.process.transformers.flow.ControlflowTransformer
import net.spartanb312.grunt.utils.builder.*
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import kotlin.random.Random

object ReplaceIf {

    fun generate(
        insnNode: JumpInsnNode,
        targetLabel: LabelNode,
        methodNode: MethodNode,
        returnType: Type,
        reverse: Boolean
    ): InsnList {
        return insnList {
            val reversedOpcode = ifComparePairs[insnNode.opcode]
            if (ControlflowTransformer.reverseExistedIf && reversedOpcode != null
                && Random.nextInt(100) <= ControlflowTransformer.reverseChance
            ) {
                val junkLabel = Label()
                +JumpInsnNode(reversedOpcode, getLabelNode(junkLabel))
                +ReplaceGoto.generate(targetLabel, methodNode, returnType, reverse)
                LABEL(junkLabel)
            } else {
                val delegateLabel = Label()
                val elseLabel = Label()
                +JumpInsnNode(insnNode.opcode, getLabelNode(delegateLabel))
                GOTO(elseLabel)
                LABEL(delegateLabel)
                +ReplaceGoto.generate(targetLabel, methodNode, returnType, reverse)
                LABEL(elseLabel)
            }
        }
    }

    val ifComparePairs = mutableMapOf(
        Opcodes.IF_ICMPEQ to Opcodes.IF_ICMPNE,
        Opcodes.IF_ICMPLE to Opcodes.IF_ICMPGT,
        Opcodes.IF_ICMPGE to Opcodes.IF_ICMPLT,
        Opcodes.IF_ICMPNE to Opcodes.IF_ICMPEQ,
        Opcodes.IF_ICMPGT to Opcodes.IF_ICMPLE,
        Opcodes.IF_ICMPLT to Opcodes.IF_ICMPGE,
    )

}