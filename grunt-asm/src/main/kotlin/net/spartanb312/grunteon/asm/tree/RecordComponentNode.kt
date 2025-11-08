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

import net.spartanb312.grunteon.asm.AnnotationVisitor
import net.spartanb312.grunteon.asm.ClassVisitor
import net.spartanb312.grunteon.asm.RecordComponentVisitor
import org.objectweb.asm.Attribute
import org.objectweb.asm.TypePath


/**
 * A node that represents a record component.
 *
 * @author Remi Forax
 * @author Luna
 */
interface RecordComponentNode : Node {
    val name: String
    val descriptor: String
    val signature: String?

    val visibleAnnotations: List<AnnotationNode>
    val invisibleAnnotations: List<AnnotationNode>
    val visibleTypeAnnotations: List<TypeAnnotationNode>
    val invisibleTypeAnnotations: List<TypeAnnotationNode>
    val attrs: List<Attribute>

    fun accept(classVisitor: ClassVisitor) {
        val recordComponentVisitor = classVisitor.visitRecordComponent(name, descriptor, signature) ?: return

        // Visit the annotations.
        visibleAnnotations.forEach {
            it.accept(recordComponentVisitor.visitAnnotation(it.desc, true))
        }
        invisibleAnnotations.forEach {
            it.accept(recordComponentVisitor.visitAnnotation(it.desc, false))
        }
        visibleTypeAnnotations.forEach {
            it.accept(recordComponentVisitor.visitTypeAnnotation(it.typeRef, it.typePath, it.desc, true))
        }
        invisibleTypeAnnotations.forEach {
            it.accept(recordComponentVisitor.visitTypeAnnotation(it.typeRef, it.typePath, it.desc, false))
        }
        // Visit the non standard attributes.
        attrs.forEach {
            recordComponentVisitor.visitAttribute(it)
        }
        recordComponentVisitor.visitEnd()
    }
}

interface MutableRecordComponentNode : RecordComponentNode, RecordComponentVisitor {
    override var name: String
    override var descriptor: String
    override var signature: String?

    override val visibleAnnotations: MutableList<AnnotationNode>
    override val invisibleAnnotations: MutableList<AnnotationNode>
    override val visibleTypeAnnotations: MutableList<TypeAnnotationNode>
    override val invisibleTypeAnnotations: MutableList<TypeAnnotationNode>
    override val attrs: MutableList<Attribute>


    // -----------------------------------------------------------------------------------------------
    // Implementation of the FieldVisitor abstract class
    // -----------------------------------------------------------------------------------------------
    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
        val annotation = nodeFactory.Annotation(desc = descriptor)
        if (visible) {
            visibleAnnotations.add(annotation)
        } else {
            invisibleAnnotations.add(annotation)
        }
        return annotation
    }

    override fun visitTypeAnnotation(
        typeRef: Int,
        typePath: TypePath?,
        descriptor: String,
        visible: Boolean
    ): AnnotationVisitor {
        val typeAnnotation = nodeFactory.TypeAnnotationNode(
            desc = descriptor,
            typeRef = typeRef,
            typePath = typePath
        )
        if (visible) {
            visibleTypeAnnotations.add(typeAnnotation)
        } else {
            invisibleTypeAnnotations.add(typeAnnotation)
        }
        return typeAnnotation
    }

    override fun visitAttribute(attribute: Attribute) {
        attrs.add(attribute)
    }

    override fun visitEnd() {
        // Nothing to do.
    }
}
