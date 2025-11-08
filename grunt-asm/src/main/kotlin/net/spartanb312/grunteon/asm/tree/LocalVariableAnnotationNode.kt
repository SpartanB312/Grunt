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

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.TypePath

/**
 * A node that represents a type annotation on a local or resource variable.
 *
 * @author Eric Bruneton
 */
class LocalVariableAnnotationNode(
    api: Int,
    typeRef: Int,
    typePath: TypePath?,
    start: Array<LabelNode?>?,
    end: Array<LabelNode?>?,
    index: IntArray?,
    descriptor: String?
) : TypeAnnotationNode(api, typeRef, typePath, descriptor) {
    /**
     * The fist instructions corresponding to the continuous ranges that make the scope of this local
     * variable (inclusive). Must not be null.
     */
    var start: MutableList<LabelNode?>

    /**
     * The last instructions corresponding to the continuous ranges that make the scope of this local
     * variable (exclusive). This list must have the same size as the 'start' list. Must not be
     * null.
     */
    var end: MutableList<LabelNode?>

    /**
     * The local variable's index in each range. This list must have the same size as the 'start'
     * list. Must not be null.
     */
    var index: MutableList<Int?>

    /**
     * Constructs a new [LocalVariableAnnotationNode]. *Subclasses must not use this
     * constructor*. Instead, they must use the [.LocalVariableAnnotationNode] version.
     *
     * @param typeRef a reference to the annotated type. See [org.objectweb.asm.TypeReference].
     * @param typePath the path to the annotated type argument, wildcard bound, array element type, or
     * static inner type within 'typeRef'. May be null if the annotation targets
     * 'typeRef' as a whole.
     * @param start the fist instructions corresponding to the continuous ranges that make the scope
     * of this local variable (inclusive).
     * @param end the last instructions corresponding to the continuous ranges that make the scope of
     * this local variable (exclusive). This array must have the same size as the 'start' array.
     * @param index the local variable's index in each range. This array must have the same size as
     * the 'start' array.
     * @param descriptor the class descriptor of the annotation class.
     */
    constructor(
        typeRef: Int,
        typePath: TypePath?,
        start: Array<LabelNode?>?,
        end: Array<LabelNode?>?,
        index: IntArray?,
        descriptor: String?
    ) : this( /* latest api = */Opcodes.ASM9, typeRef, typePath, start, end, index, descriptor)

    /**
     * Constructs a new [LocalVariableAnnotationNode].
     *
     * @param api the ASM API version implemented by this visitor. Must be one of the `ASM`*x* values in [Opcodes].
     * @param typeRef a reference to the annotated type. See [org.objectweb.asm.TypeReference].
     * @param start the fist instructions corresponding to the continuous ranges that make the scope
     * of this local variable (inclusive).
     * @param end the last instructions corresponding to the continuous ranges that make the scope of
     * this local variable (exclusive). This array must have the same size as the 'start' array.
     * @param index the local variable's index in each range. This array must have the same size as
     * the 'start' array.
     * @param typePath the path to the annotated type argument, wildcard bound, array element type, or
     * static inner type within 'typeRef'. May be null if the annotation targets
     * 'typeRef' as a whole.
     * @param descriptor the class descriptor of the annotation class.
     */
    init {
        this.start = Util.asArrayList<LabelNode?>(start)
        this.end = Util.asArrayList<LabelNode?>(end)
        this.index = Util.asArrayList(index)
    }

    /**
     * Makes the given visitor visit this type annotation.
     *
     * @param methodVisitor the visitor that must visit this annotation.
     * @param visible true if the annotation is visible at runtime.
     */
    fun accept(methodVisitor: MethodVisitor, visible: Boolean) {
        val startLabels = arrayOfNulls<Label>(this.start.size)
        val endLabels = arrayOfNulls<Label>(this.end.size)
        val indices = IntArray(this.index.size)
        var i = 0
        val n = startLabels.size
        while (i < n) {
            startLabels[i] = this.start.get(i)!!.getLabel()
            endLabels[i] = this.end.get(i)!!.getLabel()
            indices[i] = this.index.get(i)!!
            ++i
        }
        accept(
            methodVisitor.visitLocalVariableAnnotation(
                typeRef, typePath, startLabels, endLabels, indices, desc, visible
            )
        )
    }
}
