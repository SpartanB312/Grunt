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

/**
 * A node that represents a field.
 *
 * @author Eric Bruneton
 */
class FieldNode
/**
 * Constructs a new [FieldNode].
 *
 * @param api the ASM API version implemented by this visitor. Must be one of the `ASM`*x* values in [Opcodes].
 * @param access the field's access flags (see [Opcodes]). This parameter
 * also indicates if the field is synthetic and/or deprecated.
 * @param name the field's name.
 * @param desc the field's descriptor (see [org.objectweb.asm.Type]).
 * @param signature the field's signature.
 * @param value the field's initial value. This parameter, which may be null if the
 * field does not have an initial value, must be an [Integer], a [Float], a [     ], a [Double] or a [String].
 */(
    api: Int,
    /**
     * The field's access flags (see [Opcodes]). This field also indicates if
     * the field is synthetic and/or deprecated.
     */
    var access: Int,
    /** The field's name.  */
    var name: String?,
    /** The field's descriptor (see [org.objectweb.asm.Type]).  */
    var desc: String?,
    /** The field's signature. May be null.  */
    var signature: String?,
    /**
     * The field's initial value. This field, which may be null if the field does not have
     * an initial value, must be an [Integer], a [Float], a [Long], a [Double]
     * or a [String].
     */
    var value: Any?
) : FieldVisitor(api) {
    /** The runtime visible annotations of this field. May be null.  */
    var visibleAnnotations: MutableList<AnnotationNode>? = null

    /** The runtime invisible annotations of this field. May be null.  */
    var invisibleAnnotations: MutableList<AnnotationNode>? = null

    /** The runtime visible type annotations of this field. May be null.  */
    var visibleTypeAnnotations: MutableList<TypeAnnotationNode>? = null

    /** The runtime invisible type annotations of this field. May be null.  */
    var invisibleTypeAnnotations: MutableList<TypeAnnotationNode>? = null

    /** The non standard attributes of this field. * May be null.  */
    var attrs: MutableList<Attribute?>? = null

    /**
     * Constructs a new [FieldNode]. *Subclasses must not use this constructor*. Instead,
     * they must use the [.FieldNode] version.
     *
     * @param access the field's access flags (see [Opcodes]). This parameter
     * also indicates if the field is synthetic and/or deprecated.
     * @param name the field's name.
     * @param descriptor the field's descriptor (see [org.objectweb.asm.Type]).
     * @param signature the field's signature.
     * @param value the field's initial value. This parameter, which may be null if the
     * field does not have an initial value, must be an [Integer], a [Float], a [     ], a [Double] or a [String].
     * @throws IllegalStateException If a subclass calls this constructor.
     */
    constructor(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        value: Any?
    ) : this( /* latest api = */Opcodes.ASM9, access, name, descriptor, signature, value) {
        check(javaClass == FieldNode::class.java)
    }

    // -----------------------------------------------------------------------------------------------
    // Implementation of the FieldVisitor abstract class
    // -----------------------------------------------------------------------------------------------
    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor {
        val annotation: AnnotationNode = net.spartanb312.grunteon.asm.tree.AnnotationNode(descriptor)
        if (visible) {
            visibleAnnotations = Util.add<AnnotationNode?>(visibleAnnotations, annotation)
        } else {
            invisibleAnnotations = Util.add<AnnotationNode?>(invisibleAnnotations, annotation)
        }
        return annotation
    }

    override fun visitTypeAnnotation(
        typeRef: Int, typePath: TypePath?, descriptor: String?, visible: Boolean
    ): AnnotationVisitor {
        val typeAnnotation: TypeAnnotationNode = TypeAnnotationNode(typeRef, typePath, descriptor)
        if (visible) {
            visibleTypeAnnotations = Util.add<TypeAnnotationNode>(visibleTypeAnnotations, typeAnnotation)
        } else {
            invisibleTypeAnnotations = Util.add<TypeAnnotationNode>(invisibleTypeAnnotations, typeAnnotation)
        }
        return typeAnnotation
    }

    override fun visitAttribute(attribute: Attribute?) {
        attrs = Util.add<Attribute?>(attrs, attribute)
    }

    override fun visitEnd() {
        // Nothing to do.
    }

    // -----------------------------------------------------------------------------------------------
    // Accept methods
    // -----------------------------------------------------------------------------------------------
    /**
     * Checks that this field node is compatible with the given ASM API version. This method checks
     * that this node, and all its children recursively, do not contain elements that were introduced
     * in more recent versions of the ASM API than the given version.
     *
     * @param api an ASM API version. Must be one of the `ASM`*x* values in [     ].
     */
    fun check(api: Int) {
        if (api == Opcodes.ASM4) {
            if (visibleTypeAnnotations != null && !visibleTypeAnnotations!!.isEmpty()) {
                throw UnsupportedClassVersionException()
            }
            if (invisibleTypeAnnotations != null && !invisibleTypeAnnotations!!.isEmpty()) {
                throw UnsupportedClassVersionException()
            }
        }
    }

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
        if (visibleAnnotations != null) {
            var i = 0
            val n = visibleAnnotations!!.size
            while (i < n) {
                val annotation = visibleAnnotations!!.get(i)
                annotation.accept(fieldVisitor.visitAnnotation(annotation.desc, true))
                ++i
            }
        }
        if (invisibleAnnotations != null) {
            var i = 0
            val n = invisibleAnnotations!!.size
            while (i < n) {
                val annotation = invisibleAnnotations!!.get(i)
                annotation.accept(fieldVisitor.visitAnnotation(annotation.desc, false))
                ++i
            }
        }
        if (visibleTypeAnnotations != null) {
            var i = 0
            val n = visibleTypeAnnotations!!.size
            while (i < n) {
                val typeAnnotation: TypeAnnotationNode = visibleTypeAnnotations!!.get(i)
                typeAnnotation.accept(
                    fieldVisitor.visitTypeAnnotation(
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
                    fieldVisitor.visitTypeAnnotation(
                        typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, false
                    )
                )
                ++i
            }
        }
        // Visit the non standard attributes.
        if (attrs != null) {
            var i = 0
            val n = attrs!!.size
            while (i < n) {
                fieldVisitor.visitAttribute(attrs!!.get(i))
                ++i
            }
        }
        fieldVisitor.visitEnd()
    }
}
