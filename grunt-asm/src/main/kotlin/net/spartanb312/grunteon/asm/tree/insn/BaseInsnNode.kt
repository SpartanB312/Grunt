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
import net.spartanb312.grunteon.asm.tree.TypeAnnotationNode

/**
 * A node that represents a bytecode instruction. *An instruction can appear at most once in at
 * most one [net.spartanb312.grunteon.asm.tree.InsnList] at a time*.
 *
 * @author Eric Bruneton
 * @author Luna
 */
sealed interface IBaseInsnNode {
    /**
     * The opcode of this instruction, or -1 if this is not a JVM instruction (e.g. a label or a line
     * number).
     */
    val opcode: Int

    /**
     * The runtime visible type annotations of this instruction. This field is only used for real
     * instructions (i.e. not for labels, frames, or line number nodes). This list is a list of [ ] objects. May be null.
     */
    val visibleTypeAnnotations: List<TypeAnnotationNode>

    /**
     * The runtime invisible type annotations of this instruction. This field is only used for real
     * instructions (i.e. not for labels, frames, or line number nodes). This list is a list of [ ] objects. May be null.
     */
    val invisibleTypeAnnotations: List<TypeAnnotationNode>


    /**
     * Returns the type of this instruction.
     *
     * @return the type of this instruction, i.e. one the constants defined in this class.
     */
    val type: Int

    /**
     * Makes the given method visitor visit this instruction.
     *
     * @param methodVisitor a method visitor.
     */
    fun accept(methodVisitor: MethodVisitor)

    companion object {
        /**
         * Makes the given visitor visit the annotations of this instruction.
         *
         * @param methodVisitor a method visitor.
         */
        internal fun acceptAnnotations(node: IBaseInsnNode, methodVisitor: MethodVisitor) {
            node.visibleTypeAnnotations.forEach {
                it.accept(methodVisitor.visitTypeAnnotation(it.typeRef, it.typePath, it.desc, true))
            }
            node.invisibleTypeAnnotations.forEach {
                it.accept(methodVisitor.visitTypeAnnotation(it.typeRef, it.typePath, it.desc, false))
            }
        }
    }
}