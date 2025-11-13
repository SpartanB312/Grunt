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
 * A node that represents a try catch block.
 *
 * @author Eric Bruneton
 * @author Luna
 */
interface TryCatchBlockNode : Node {
    /** The beginning of the exception handler's scope (inclusive).  */
    val start: LabelNode

    /** The end of the exception handler's scope (exclusive).  */
    val end: LabelNode

    /** The beginning of the exception handler's code.  */
    val handler: LabelNode?

    /**
     * The internal name of the type of exceptions handled by the handler. May be null to
     * catch any exceptions (for "finally" blocks).
     */
    val type: String?

    /** The runtime visible type annotations on the exception handler type. May be null.  */
    val visibleTypeAnnotations: List<TypeAnnotationNode>

    /**
     * The runtime invisible type annotations on the exception handler type. May be null.
     */
    val invisibleTypeAnnotations: List<TypeAnnotationNode>

    /**
     * Makes the given visitor visit this try catch block.
     *
     * @param methodVisitor a method visitor.
     */
    fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitTryCatchBlock(
            methodVisitor.getLabel(start),
            methodVisitor.getLabel(end),
            handler?.let { methodVisitor.getLabel(it) },
            type
        )
        visibleTypeAnnotations.forEach { node ->
            methodVisitor.visitTryCatchAnnotation(node.typeRef, node.typePath, node.desc, true)?.let {
                node.accept(it)
            }
        }
        invisibleTypeAnnotations.forEach { node ->
            methodVisitor.visitTryCatchAnnotation(node.typeRef, node.typePath, node.desc, false)?.let {
                node.accept(it)
            }
        }
    }
}

interface MutableTryCatchBlockNode : TryCatchBlockNode {
    override var start: LabelNode
    override var end: LabelNode
    override var handler: LabelNode
    override var type: String?
    override val visibleTypeAnnotations: MutableList<TypeAnnotationNode>
    override val invisibleTypeAnnotations: MutableList<TypeAnnotationNode>

    /**
     * Updates the index of this try catch block in the method's list of try catch block nodes. This
     * index maybe stored in the 'target' field of the type annotations of this block.
     *
     * @param index the new index of this try catch block in the method's list of try catch block
     * nodes.
     */
    fun updateIndex(index: Int) {
        val newTypeRef = 0x42000000 or (index shl 8)
        visibleTypeAnnotations.forEachIndexed { i, node ->
            visibleTypeAnnotations[i] = nodeFactory.TypeAnnotationNode(
                node,
                typeRef = newTypeRef
            )
        }
        invisibleTypeAnnotations.forEachIndexed { i, node ->
            invisibleTypeAnnotations[i] = nodeFactory.TypeAnnotationNode(
                node,
                typeRef = newTypeRef
            )
        }
    }
}