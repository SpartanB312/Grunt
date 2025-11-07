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

/**
 * A visitor to visit a Java annotation. The methods of this class must be called in the following
 * order: ( `visit` | `visitEnum` | `visitAnnotation` | `visitArray` )*
 * `visitEnd`.
 *
 * @author Eric Bruneton
 * @author Eugene Kuleshov
 * @author Luna
 */
interface AnnotationVisitor {
    /**
     * Visits a primitive value of the annotation.
     *
     * @param name the value name.
     * @param value the actual value, whose type must be [Byte], [Boolean], [Char], [Short], [Integer] , [Long], [Float], [Double],
     * [String] or [Type] of [Type.OBJECT] or [Type.ARRAY] sort. This
     * value can also be an array of byte, boolean, short, char, int, long, float or double values
     * (this is equivalent to using [.visitArray] and visiting each array element in turn,
     * but is more convenient).
     */
    fun visit(name: String?, value: Any) {}

    /**
     * Visits an enumeration value of the annotation.
     *
     * @param name the value name.
     * @param descriptor the class descriptor of the enumeration class.
     * @param value the actual enumeration value.
     */
    fun visitEnum(name: String?, descriptor: String, value: String) {}

    /**
     * Visits a nested annotation value of the annotation.
     *
     * @param name the value name.
     * @param descriptor the class descriptor of the nested annotation class.
     * @return a visitor to visit the actual nested annotation value, or null if this
     * visitor is not interested in visiting this nested annotation. *The nested annotation
     * value must be fully visited before calling other methods on this annotation visitor*.
     */
    fun visitAnnotation(name: String?, descriptor: String?): AnnotationVisitor? = null

    /**
     * Visits an array value of the annotation. Note that arrays of primitive values (such as byte,
     * boolean, short, char, int, long, float or double) can be passed as value to [ visit][.visit]. This is what [ClassReader] does for non empty arrays of primitive values.
     *
     * @param name the value name.
     * @return a visitor to visit the actual array value elements, or null if this visitor
     * is not interested in visiting these values. The 'name' parameters passed to the methods of
     * this visitor are ignored. *All the array values must be visited before calling other
     * methods on this annotation visitor*.
     */
    fun visitArray(name: String?): AnnotationVisitor? = null

    /** Visits the end of the annotation.  */
    fun visitEnd() {}

    class FromOw2(val ow2: org.objectweb.asm.AnnotationVisitor) : AnnotationVisitor {
        override fun visit(name: String?, value: Any) {
            ow2.visit(name, value)
        }

        override fun visitEnum(name: String?, descriptor: String, value: String) {
            ow2.visitEnum(name, descriptor, value)
        }

        override fun visitAnnotation(name: String?, descriptor: String?): AnnotationVisitor? {
            return ow2.visitAnnotation(name, descriptor)?.let { FromOw2(it) }
        }

        override fun visitArray(name: String?): AnnotationVisitor? {
            return ow2.visitArray(name)?.let { FromOw2(it) }
        }

        override fun visitEnd() {
            ow2.visitEnd()
        }
    }

    class ToOw2(val grunt: AnnotationVisitor) : org.objectweb.asm.AnnotationVisitor(org.objectweb.asm.Opcodes.ASM9) {
        override fun visit(name: String?, value: Any) {
            grunt.visit(name, value)
        }

        override fun visitEnum(name: String?, descriptor: String, value: String) {
            grunt.visitEnum(name, descriptor, value)
        }

        override fun visitAnnotation(name: String?, descriptor: String?): org.objectweb.asm.AnnotationVisitor? {
            return grunt.visitAnnotation(name, descriptor)?.let { ToOw2(it) }
        }

        override fun visitArray(name: String?): org.objectweb.asm.AnnotationVisitor {
            return grunt.visitArray(name)?.let { ToOw2(it) }!!
        }

        override fun visitEnd() {
            grunt.visitEnd()
        }
    }
}
