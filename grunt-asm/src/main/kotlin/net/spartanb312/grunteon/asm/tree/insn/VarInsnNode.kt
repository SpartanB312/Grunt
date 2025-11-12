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
 * A node that represents a local variable instruction. A local variable instruction is an
 * instruction that loads or stores the value of a local variable.
 *
 * @author Eric Bruneton
 * @author Luna
 */
sealed interface IVarInsnNode : IBaseInsnNode {
    override val opcode: Int

    /** The operand of this instruction. This operand is the index of a local variable.  */
    val variable: Int

    override val type: Int
        get() = AbstractInsnNode.VAR_INSN

    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitVarInsn(opcode, variable)
        IBaseInsnNode.acceptAnnotations(this, methodVisitor)
    }
}

interface ILoadInsnNode : IVarInsnNode {
    override val opcode: Int
        get() = Opcodes.ILOAD
}

interface LLoadInsnNode : IVarInsnNode {
    override val opcode: Int
        get() = Opcodes.LLOAD
}

interface FLoadInsnNode : IVarInsnNode {
    override val opcode: Int
        get() = Opcodes.FLOAD
}

interface DLoadInsnNode : IVarInsnNode {
    override val opcode: Int
        get() = Opcodes.DLOAD
}

interface ALoadInsnNode : IVarInsnNode {
    override val opcode: Int
        get() = Opcodes.ALOAD
}

interface IStoreInsnNode : IVarInsnNode {
    override val opcode: Int
        get() = Opcodes.ISTORE
}

interface LStoreInsnNode : IVarInsnNode {
    override val opcode: Int
        get() = Opcodes.LSTORE
}

interface FStoreInsnNode : IVarInsnNode {
    override val opcode: Int
        get() = Opcodes.FSTORE
}

interface DStoreInsnNode : IVarInsnNode {
    override val opcode: Int
        get() = Opcodes.DSTORE
}

interface AStoreInsnNode : IVarInsnNode {
    override val opcode: Int
        get() = Opcodes.ASTORE
}