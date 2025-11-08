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
package net.spartanb312.grunteon.asm.tree

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * A node that represents a MULTIANEWARRAY instruction.
 *
 * @author Eric Bruneton
 */
class MultiANewArrayInsnNode
/**
 * Constructs a new [MultiANewArrayInsnNode].
 *
 * @param desc an array type descriptor (see [org.objectweb.asm.Type]).
 * @param dims the number of dimensions of the array to allocate.
 */(
    /** An array type descriptor (see [org.objectweb.asm.Type]).  */
    var desc: String?,
    /** Number of dimensions of the array to allocate.  */
    var dims: Int
) : AbstractInsnNode(Opcodes.MULTIANEWARRAY) {
    override fun getType(): Int {
        return AbstractInsnNode.Companion.MULTIANEWARRAY_INSN
    }

    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitMultiANewArrayInsn(desc, dims)
        acceptAnnotations(methodVisitor)
    }

    override fun clone(clonedLabels: MutableMap<LabelNode?, LabelNode?>?): AbstractInsnNode {
        return MultiANewArrayInsnNode(desc, dims).cloneAnnotations(this)
    }
}
