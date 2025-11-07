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
package net.spartanb312.grunteon.asm

import org.objectweb.asm.Attribute
import org.objectweb.asm.TypePath

/**
 * A visitor to visit a Java field. The methods of this class must be called in the following order:
 * ( `visitAnnotation` | `visitTypeAnnotation` | `visitAttribute` )* `visitEnd`.
 *
 * @author Eric Bruneton
 * @author Luna
 */
interface FieldVisitor {

    /**
     * Visits an annotation of the field.
     *
     * @param descriptor the class descriptor of the annotation class.
     * @param visible true if the annotation is visible at runtime.
     * @return a visitor to visit the annotation values, or null if this visitor is not
     * interested in visiting this annotation.
     */
    fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? = null

    /**
     * Visits an annotation on the type of the field.
     *
     * @param typeRef a reference to the annotated type. The sort of this type reference must be
     * [TypeReference.FIELD]. See [TypeReference].
     * @param typePath the path to the annotated type argument, wildcard bound, array element type, or
     * static inner type within 'typeRef'. May be null if the annotation targets
     * 'typeRef' as a whole.
     * @param descriptor the class descriptor of the annotation class.
     * @param visible true if the annotation is visible at runtime.
     * @return a visitor to visit the annotation values, or null if this visitor is not
     * interested in visiting this annotation.
     */
    fun visitTypeAnnotation(
        typeRef: Int, typePath: TypePath?, descriptor: String, visible: Boolean
    ): AnnotationVisitor? = null

    /**
     * Visits a non standard attribute of the field.
     *
     * @param attribute an attribute.
     */
    fun visitAttribute(attribute: Attribute) {}

    /**
     * Visits the end of the field. This method, which is the last one to be called, is used to inform
     * the visitor that all the annotations and attributes of the field have been visited.
     */
    fun visitEnd()

    class FromOw2(val ow2: org.objectweb.asm.FieldVisitor) : FieldVisitor {
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            return ow2.visitAnnotation(descriptor, visible)?.let { AnnotationVisitor.FromOw2(it) }
        }

        override fun visitTypeAnnotation(
            typeRef: Int,
            typePath: TypePath?,
            descriptor: String,
            visible: Boolean
        ): AnnotationVisitor? {
            return ow2.visitTypeAnnotation(typeRef, typePath, descriptor, visible)
                ?.let { AnnotationVisitor.FromOw2(it) }
        }

        override fun visitAttribute(attribute: Attribute) {
            ow2.visitAttribute(attribute)
        }

        override fun visitEnd() {
            ow2.visitEnd()
        }
    }

    class ToOw2(val toOw2: org.objectweb.asm.FieldVisitor) : FieldVisitor {
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            return toOw2.visitAnnotation(descriptor, visible)?.let { AnnotationVisitor.ToOw2(it) }
        }

        override fun visitTypeAnnotation(
            typeRef: Int,
            typePath: TypePath?,
            descriptor: String,
            visible: Boolean
        ): AnnotationVisitor? {
            return toOw2.visitTypeAnnotation(typeRef, typePath, descriptor, visible)
                ?.let { AnnotationVisitor.ToOw2(it) }
        }

        override fun visitAttribute(attribute: Attribute) {
            toOw2.visitAttribute(attribute)
        }

        override fun visitEnd() {
            toOw2.visitEnd()
        }
    }
}
