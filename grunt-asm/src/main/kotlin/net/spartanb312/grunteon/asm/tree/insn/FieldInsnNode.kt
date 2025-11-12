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
 * A node that represents a field instruction. A field instruction is an instruction that loads or
 * stores the value of a field of an object.
 *
 * @author Eric Bruneton
 * @author Luna
 */
sealed interface IFieldInsnNode : IBaseInsnNode {
    override val opcode: Int

    /**
     * The internal name of the field's owner class (see [ ][org.objectweb.asm.Type.getInternalName]).
     */
    val owner: String

    /** The field's name.  */
    val name: String

    /** The field's descriptor (see [org.objectweb.asm.Type]).  */
    val desc: String

    override val type: Int
        get() = AbstractInsnNode.FIELD_INSN

    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitFieldInsn(opcode, owner, name, desc)
        IBaseInsnNode.acceptAnnotations(this, methodVisitor)
    }
}

interface GetStaticInsnNode : IFieldInsnNode {
    override val opcode: Int
        get() = Opcodes.GETSTATIC
}

interface PutStaticInsnNode : IFieldInsnNode {
    override val opcode: Int
        get() = Opcodes.PUTSTATIC
}

interface GetFieldInsnNode : IFieldInsnNode {
    override val opcode: Int
        get() = Opcodes.GETFIELD
}

interface PutFieldInsnNode : IFieldInsnNode {
    override val opcode: Int
        get() = Opcodes.PUTFIELD
}