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
import org.objectweb.asm.tree.AbstractInsnNode

/**
 * A node that represents a type instruction. A type instruction is an instruction which takes an
 * internal name as parameter (see [org.objectweb.asm.Type.getInternalName]).
 *
 * @author Eric Bruneton
 * @author Luna
 */
interface TypeInsnNode : BaseInsnNode {
    override val opcode: Int

    /**
     * The operand of this instruction. Despite its name (due to historical reasons), this operand is
     * an internal name (see [org.objectweb.asm.Type.getInternalName]).
     */
    val desc: String

    override val type: Int
        get() = AbstractInsnNode.TYPE_INSN

    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitTypeInsn(opcode, desc)
        BaseInsnNode.acceptAnnotations(this, methodVisitor)
    }
}
