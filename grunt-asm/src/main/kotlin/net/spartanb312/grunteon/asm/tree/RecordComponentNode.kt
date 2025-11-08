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

import net.spartanb312.grunteon.asm.tree.AnnotationNode.accept
import org.objectweb.asm.*

/** A node that represents a record component. */
interface RecordComponentNode {
    val name: String?
    val descriptor: String?
    val signature: String?

    val visibleAnnotations: List<AnnotationNode>?
    val invisibleAnnotations: List<AnnotationNode>?
    val visibleTypeAnnotations: List<TypeAnnotationNode>?
    val invisibleTypeAnnotations: List<TypeAnnotationNode>?
    val attrs: List<Attribute?>?

    fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? = null

    fun visitTypeAnnotation(
        typeRef: Int,
        typePath: TypePath?,
        descriptor: String?,
        visible: Boolean
    ): AnnotationVisitor? = null

    fun visitAttribute(attribute: Attribute?) {}

    fun visitEnd() {}

    fun check(api: Int) {
        if (api < Opcodes.ASM8) {
            throw UnsupportedClassVersionException()
        }
    }

    fun accept(classVisitor: ClassVisitor) {
        val recordComponentVisitor = classVisitor.visitRecordComponent(name, descriptor, signature)
        if (recordComponentVisitor == null) {
            return
        }
        // Visit the annotations.
        if (visibleAnnotations != null) {
            var i = 0
            val n = visibleAnnotations.size
            while (i < n) {
                val annotation = visibleAnnotations[i]
                annotation.accept(recordComponentVisitor.visitAnnotation(annotation.desc, true))
                ++i
            }
        }
        if (invisibleAnnotations != null) {
            var i = 0
            val n = invisibleAnnotations.size
            while (i < n) {
                val annotation = invisibleAnnotations[i]
                annotation.accept(recordComponentVisitor.visitAnnotation(annotation.desc, false))
                ++i
            }
        }
        if (visibleTypeAnnotations != null) {
            var i = 0
            val n = visibleTypeAnnotations.size
            while (i < n) {
                val typeAnnotation: TypeAnnotationNode = visibleTypeAnnotations[i]
                typeAnnotation.accept(
                    recordComponentVisitor.visitTypeAnnotation(
                        typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, true
                    )
                )
                ++i
            }
        }
        if (invisibleTypeAnnotations != null) {
            var i = 0
            val n = invisibleTypeAnnotations.size
            while (i < n) {
                val typeAnnotation: TypeAnnotationNode = invisibleTypeAnnotations[i]
                typeAnnotation.accept(
                    recordComponentVisitor.visitTypeAnnotation(
                        typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, false
                    )
                )
                ++i
            }
        }
        // Visit the non standard attributes.
        if (attrs != null) {
            var i = 0
            val n = attrs.size
            while (i < n) {
                recordComponentVisitor.visitAttribute(attrs[i])
                ++i
            }
        }
        recordComponentVisitor.visitEnd()
    }
}

interface MutableRecordComponentNode : RecordComponentNode {
    override var name: String?
    override var descriptor: String?
    override var signature: String?

    override var visibleAnnotations: MutableList<AnnotationNode>?
    override var invisibleAnnotations: MutableList<AnnotationNode>?
    override var visibleTypeAnnotations: MutableList<TypeAnnotationNode>?
    override var invisibleTypeAnnotations: MutableList<TypeAnnotationNode>?
    override var attrs: MutableList<Attribute?>?

    companion object {
        class Impl(
            override var name: String?,
            override var descriptor: String?,
            override var signature: String?
        ) : MutableRecordComponentNode {
            override var visibleAnnotations: MutableList<AnnotationNode>? = null
            override var invisibleAnnotations: MutableList<AnnotationNode>? = null
            override var visibleTypeAnnotations: MutableList<TypeAnnotationNode>? = null
            override var invisibleTypeAnnotations: MutableList<TypeAnnotationNode>? = null
            override var attrs: MutableList<Attribute?>? = null

            // The visitor implementations are provided by the interface defaults.
            override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
                val annotation: AnnotationNode = net.spartanb312.grunteon.asm.tree.AnnotationNode(descriptor)
                if (visible) {
                    visibleAnnotations = Util.add(visibleAnnotations, annotation)
                } else {
                    invisibleAnnotations = Util.add(invisibleAnnotations, annotation)
                }
                return annotation
            }

            override fun visitTypeAnnotation(
                typeRef: Int,
                typePath: TypePath?,
                descriptor: String?,
                visible: Boolean
            ): AnnotationVisitor? {
                val typeAnnotation: TypeAnnotationNode = TypeAnnotationNode(typeRef, typePath, descriptor)
                if (visible) {
                    visibleTypeAnnotations = Util.add(visibleTypeAnnotations, typeAnnotation)
                } else {
                    invisibleTypeAnnotations = Util.add(invisibleTypeAnnotations, typeAnnotation)
                }
                return typeAnnotation
            }

            override fun visitAttribute(attribute: Attribute?) {
                attrs = Util.add(attrs, attribute)
            }
        }
    }
}
