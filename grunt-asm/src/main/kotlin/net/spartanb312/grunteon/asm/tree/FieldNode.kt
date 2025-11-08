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
import net.spartanb312.grunteon.asm.FieldVisitor
import org.objectweb.asm.Attribute
import org.objectweb.asm.TypePath

/**
 * A node that represents a field.
 *
 * @author Eric Bruneton
 * @author Luna
 */
interface FieldNode : Node {

    /**
     * The field's access flags (see [Opcodes]). This field also indicates if
     * the field is synthetic and/or deprecated.
     */
    val access: Int

    /** The field's name.  */
    val name: String

    /** The field's descriptor (see [org.objectweb.asm.Type]).  */
    val desc: String

    /** The field's signature. May be null.  */
    val signature: String?

    /**
     * The field's initial value. This field, which may be null if the field does not have
     * an initial value, must be an [Integer], a [Float], a [Long], a [Double]
     * or a [String].
     */
    val value: Any?

    /** The runtime visible _root_ide_package_.kotlin.collections.List of this field. May be null.  */
    val visibleAnnotations: List<AnnotationNode>

    /** The runtime invisible annotations of this field. May be null.  */
    val invisibleAnnotations: List<AnnotationNode>

    /** The runtime visible type annotations of this field. May be null.  */
    val visibleTypeAnnotations: List<TypeAnnotationNode>

    /** The runtime invisible type annotations of this field. May be null.  */
    val invisibleTypeAnnotations: List<TypeAnnotationNode>

    /** The non standard attributes of this field. * May be null.  */
    val attrs: List<Attribute>

    // -----------------------------------------------------------------------------------------------
    // Accept methods
    // -----------------------------------------------------------------------------------------------
    /**
     * Makes the given class visitor visit this field.
     *
     * @param classVisitor a class visitor.
     */
    fun accept(classVisitor: ClassVisitor) {
        val fieldVisitor = classVisitor.visitField(access, name, desc, signature, value)
        if (fieldVisitor == null) {
            return
        }
        // Visit the annotations.
        visibleAnnotations.forEach {
            it.accept(fieldVisitor.visitAnnotation(it.desc, true))
        }
        invisibleAnnotations.forEach {
            it.accept(fieldVisitor.visitAnnotation(it.desc, false))
        }
        visibleTypeAnnotations.forEach {
            it.accept(fieldVisitor.visitTypeAnnotation(it.typeRef, it.typePath, it.desc, true))
        }
        invisibleTypeAnnotations.forEach {
            it.accept(fieldVisitor.visitTypeAnnotation(it.typeRef, it.typePath, it.desc, false))
        }
        // Visit the non standard attributes.
        attrs.forEach {
            fieldVisitor.visitAttribute(it)
        }
        fieldVisitor.visitEnd()
    }
}

interface MutableFieldNode : FieldNode, FieldVisitor {
    override var access: Int
    override var name: String
    override var desc: String
    override var signature: String?
    override var value: Any?
    override val visibleAnnotations: MutableList<AnnotationNode>
    override val invisibleAnnotations: MutableList<AnnotationNode>
    override val visibleTypeAnnotations: MutableList<TypeAnnotationNode>
    override val invisibleTypeAnnotations: MutableList<TypeAnnotationNode>
    override val attrs: MutableList<Attribute>

    // -----------------------------------------------------------------------------------------------
    // Implementation of the FieldVisitor abstract class
    // -----------------------------------------------------------------------------------------------
    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
        val annotation: MutableAnnotationNode = nodeFactory.Annotation(desc = descriptor)
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