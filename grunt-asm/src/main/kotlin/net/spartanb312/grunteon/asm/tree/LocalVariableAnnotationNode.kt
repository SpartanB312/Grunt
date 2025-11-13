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

import net.spartanb312.grunteon.asm.MethodVisitor
import net.spartanb312.grunteon.asm.tree.insn.LabelNode

/**
 * A node that represents a type annotation on a local or resource variable.
 *
 * @author Eric Bruneton
 */
interface LocalVariableAnnotationNode : TypeAnnotationNode {
    /**
     * The fist instructions corresponding to the continuous ranges that make the scope of this local
     * variable (inclusive). Must not be null.
     */
    val start: List<LabelNode>

    /**
     * The last instructions corresponding to the continuous ranges that make the scope of this local
     * variable (exclusive). This list must have the same size as the 'start' list. Must not be
     * null.
     */
    val end: List<LabelNode>

    /**
     * The local variable's index in each range. This list must have the same size as the 'start'
     * list. Must not be null.
     */
    val index: List<Int>

    /**
     * Makes the given visitor visit this type annotation.
     *
     * @param methodVisitor the visitor that must visit this annotation.
     * @param visible true if the annotation is visible at runtime.
     */
    fun accept(methodVisitor: MethodVisitor, visible: Boolean) {
        accept(
            methodVisitor.visitLocalVariableAnnotation(
                typeRef, typePath, start.map { it.value }, end.map { it.value }, index, desc, visible
            )
        )
    }
}

interface MutableLocalVariableAnnotationNode : LocalVariableAnnotationNode {
    override val start: MutableList<LabelNode>
    override val end: MutableList<LabelNode>
    override val index: MutableList<Int>
}