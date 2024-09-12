package net.spartanb312.grunt.process.transformers.flow.process

import net.spartanb312.grunt.utils.builder.GOTO
import net.spartanb312.grunt.utils.builder.LABEL
import net.spartanb312.grunt.utils.builder.getLabelNode
import net.spartanb312.grunt.utils.builder.insnList
import org.objectweb.asm.Label
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode

object ReplaceIf {

    fun generate(
        insnNode: JumpInsnNode,
        targetLabel: LabelNode,
        methodNode: MethodNode,
        returnType: Type
    ): InsnList {
        return insnList {
            val delegateLabel = Label()
            val elseLabel = Label()
            +JumpInsnNode(insnNode.opcode, getLabelNode(delegateLabel))
            GOTO(elseLabel)
            LABEL(delegateLabel)
            +ReplaceGoto.generate(targetLabel, methodNode, returnType)
            LABEL(elseLabel)
        }
    }

}