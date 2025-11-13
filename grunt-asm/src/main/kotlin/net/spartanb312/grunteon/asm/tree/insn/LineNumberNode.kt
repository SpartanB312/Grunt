package net.spartanb312.grunteon.asm.tree.insn

import net.spartanb312.grunteon.asm.MethodVisitor
import org.objectweb.asm.tree.AbstractInsnNode

/**
 * A node that represents a line number declaration. These nodes are pseudo instruction nodes in
 * order to be inserted in an instruction list.
 *
 * @author Eric Bruneton
 * @author Luna
 */
interface LineNumberNode : IBaseInsnNode {
    override val opcode: Int
        get() = -1

    /** A line number. This number refers to the source file from which the class was compiled.  */
    val line: Int

    /** The first instruction corresponding to this line number.  */
    val start: LabelNode

    override val type: Int
        get() = AbstractInsnNode.LINE

    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitLineNumber(line, methodVisitor.getLabel(start))
    }
}