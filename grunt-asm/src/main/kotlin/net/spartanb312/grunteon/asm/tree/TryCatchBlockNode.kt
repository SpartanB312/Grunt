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

import net.spartanb312.grunteon.asm.tree.insn.LabelNode
import org.objectweb.asm.MethodVisitor

/**
 * A node that represents a try catch block.
 *
 * @author Eric Bruneton
 */
class TryCatchBlockNode
/**
 * Constructs a new [TryCatchBlockNode].
 *
 * @param start the beginning of the exception handler's scope (inclusive).
 * @param end the end of the exception handler's scope (exclusive).
 * @param handler the beginning of the exception handler's code.
 * @param type the internal name of the type of exceptions handled by the handler (see [     ][org.objectweb.asm.Type.getInternalName]), or null to catch any exceptions (for
 * "finally" blocks).
 */(
    /** The beginning of the exception handler's scope (inclusive).  */
    var start: LabelNode,
    /** The end of the exception handler's scope (exclusive).  */
    var end: LabelNode,
    /** The beginning of the exception handler's code.  */
    var handler: LabelNode?,
    /**
     * The internal name of the type of exceptions handled by the handler. May be null to
     * catch any exceptions (for "finally" blocks).
     */
    var type: String?
) {
    /** The runtime visible type annotations on the exception handler type. May be null.  */
    var visibleTypeAnnotations: MutableList<TypeAnnotationNode>? = null

    /**
     * The runtime invisible type annotations on the exception handler type. May be null.
     */
    var invisibleTypeAnnotations: MutableList<TypeAnnotationNode>? = null

    /**
     * Updates the index of this try catch block in the method's list of try catch block nodes. This
     * index maybe stored in the 'target' field of the type annotations of this block.
     *
     * @param index the new index of this try catch block in the method's list of try catch block
     * nodes.
     */
    fun updateIndex(index: Int) {
        val newTypeRef = 0x42000000 or (index shl 8)
        if (visibleTypeAnnotations != null) {
            var i = 0
            val n = visibleTypeAnnotations!!.size
            while (i < n) {
                visibleTypeAnnotations!!.get(i).typeRef = newTypeRef
                ++i
            }
        }
        if (invisibleTypeAnnotations != null) {
            var i = 0
            val n = invisibleTypeAnnotations!!.size
            while (i < n) {
                invisibleTypeAnnotations!!.get(i).typeRef = newTypeRef
                ++i
            }
        }
    }

    /**
     * Makes the given visitor visit this try catch block.
     *
     * @param methodVisitor a method visitor.
     */
    fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitTryCatchBlock(
            start.getLabel(), end.getLabel(), if (handler == null) null else handler!!.getLabel(), type
        )
        if (visibleTypeAnnotations != null) {
            var i = 0
            val n = visibleTypeAnnotations!!.size
            while (i < n) {
                val typeAnnotation: TypeAnnotationNode = visibleTypeAnnotations!!.get(i)
                typeAnnotation.accept(
                    methodVisitor.visitTryCatchAnnotation(
                        typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, true
                    )
                )
                ++i
            }
        }
        if (invisibleTypeAnnotations != null) {
            var i = 0
            val n = invisibleTypeAnnotations!!.size
            while (i < n) {
                val typeAnnotation: TypeAnnotationNode = invisibleTypeAnnotations!!.get(i)
                typeAnnotation.accept(
                    methodVisitor.visitTryCatchAnnotation(
                        typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, false
                    )
                )
                ++i
            }
        }
    }
}
