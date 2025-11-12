// ASM: a very small and fast Java bytecode manipulation framework
// Copyright (c) 2000-2011 INRIA, France Telecom
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
// 1. Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holders nor the names of its
//    contributors may be used to endorse or promote products derived from
//    this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.
package net.spartanb312.grunteon.asm.tree.insn

import net.spartanb312.grunteon.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode

/**
 * A node that represents a jump instruction. A jump instruction is an instruction that may jump to
 * another instruction.
 *
 * @author Eric Bruneton
 * @author Luna
 */
sealed interface IJumpInsnNode : IBaseInsnNode {
    override val opcode: Int

    /**
     * The operand of this instruction. This operand is a label that designates the instruction to
     * which this instruction may jump.
     */
    val label: LabelNode

    override val type: Int
        get() = AbstractInsnNode.JUMP_INSN

    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitJumpInsn(opcode, label.value)
        IBaseInsnNode.acceptAnnotations(this, methodVisitor)
    }
}

interface IfEqInsnNode : IJumpInsnNode {
    override val opcode: Int
        get() = Opcodes.IFEQ
}

interface IfNeInsnNode : IJumpInsnNode {
    override val opcode: Int
        get() = Opcodes.IFNE
}

interface IfLtInsnNode : IJumpInsnNode {
    override val opcode: Int
        get() = Opcodes.IFLT
}

interface IfGeInsnNode : IJumpInsnNode {
    override val opcode: Int
        get() = Opcodes.IFGE
}

interface IfGtInsnNode : IJumpInsnNode {
    override val opcode: Int
        get() = Opcodes.IFGT
}

interface IfLeInsnNode : IJumpInsnNode {
    override val opcode: Int
        get() = Opcodes.IFLE
}

interface IfIcmpEqInsnNode : IJumpInsnNode {
    override val opcode: Int
        get() = Opcodes.IF_ICMPEQ
}

interface IfIcmpNeInsnNode : IJumpInsnNode {
    override val opcode: Int
        get() = Opcodes.IF_ICMPNE
}

interface IfIcmpLtInsnNode : IJumpInsnNode {
    override val opcode: Int
        get() = Opcodes.IF_ICMPLT
}

interface IfIcmpGeInsnNode : IJumpInsnNode {
    override val opcode: Int
        get() = Opcodes.IF_ICMPGE
}

interface IfIcmpGtInsnNode : IJumpInsnNode {
    override val opcode: Int
        get() = Opcodes.IF_ICMPGT
}

interface IfIcmpLeInsnNode : IJumpInsnNode {
    override val opcode: Int
        get() = Opcodes.IF_ICMPLE
}

interface IfAcmpEqInsnNode : IJumpInsnNode {
    override val opcode: Int
        get() = Opcodes.IF_ACMPEQ
}

interface IfAcmpNeInsnNode : IJumpInsnNode {
    override val opcode: Int
        get() = Opcodes.IF_ACMPNE
}

interface GotoInsnNode : IJumpInsnNode {
    override val opcode: Int
        get() = Opcodes.GOTO
}

interface IfNullInsnNode : IJumpInsnNode {
    override val opcode: Int
        get() = Opcodes.IFNULL
}

interface IfNonNullInsnNode : IJumpInsnNode {
    override val opcode: Int
        get() = Opcodes.IFNONNULL
}
