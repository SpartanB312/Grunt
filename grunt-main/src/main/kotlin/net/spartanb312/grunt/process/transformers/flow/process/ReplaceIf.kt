package net.spartanb312.grunt.process.transformers.flow.process

import net.spartanb312.genesis.extensions.LABEL
import net.spartanb312.genesis.extensions.insn.GOTO
import net.spartanb312.genesis.extensions.node
import net.spartanb312.genesis.instructions
import net.spartanb312.grunt.process.transformers.flow.ControlflowTransformer
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import kotlin.random.Random

object ReplaceIf {

    fun generate(
        insnNode: JumpInsnNode,
        targetLabel: LabelNode,
        classNode: ClassNode,
        methodNode: MethodNode,
        returnType: Type,
        reverse: Boolean
    ): InsnList {
        return instructions {
            val reversedOpcode = ifComparePairs[insnNode.opcode]
            if (ControlflowTransformer.reverseExistedIf && reversedOpcode != null
                && Random.nextInt(100) <= ControlflowTransformer.reverseChance
            ) {
                val elseLabel = Label()
                +JumpInsnNode(reversedOpcode, elseLabel.node)
                +ReplaceGoto.generate(targetLabel, classNode, methodNode, returnType, reverse)
                LABEL(elseLabel)
            } else {
                val delegateLabel = Label()
                val elseLabel = Label()
                +JumpInsnNode(insnNode.opcode, delegateLabel.node)
                GOTO(elseLabel)
                LABEL(delegateLabel)
                +ReplaceGoto.generate(targetLabel, classNode, methodNode, returnType, reverse)
                LABEL(elseLabel)
            }
        }
    }

    val ifComparePairs = mutableMapOf(
        Opcodes.IFEQ to Opcodes.IFNE,
        Opcodes.IFLE to Opcodes.IFGT,
        Opcodes.IFGE to Opcodes.IFLT,
        Opcodes.IFNE to Opcodes.IFEQ,
        Opcodes.IFGT to Opcodes.IFLE,
        Opcodes.IFLT to Opcodes.IFGE,
        Opcodes.IF_ICMPEQ to Opcodes.IF_ICMPNE,
        Opcodes.IF_ICMPLE to Opcodes.IF_ICMPGT,
        Opcodes.IF_ICMPGE to Opcodes.IF_ICMPLT,
        Opcodes.IF_ICMPNE to Opcodes.IF_ICMPEQ,
        Opcodes.IF_ICMPGT to Opcodes.IF_ICMPLE,
        Opcodes.IF_ICMPLT to Opcodes.IF_ICMPGE,
    )

}