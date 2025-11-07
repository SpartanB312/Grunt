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

/**
 * A node that represents an annotation.
 *
 * @author Eric Bruneton
 * @author Luna
 */
interface AnnotationNode {
    /** The class descriptor of the annotation class.  */
    val desc: String?

    /**
     * The name value pairs of this annotation. Each name value pair is stored as two consecutive
     * elements in the list. The name is a [String], and the value may be a [Byte], [ ], [Character], [Short], [Integer], [Long], [Float],
     * [Double], [String] or [org.objectweb.asm.Type], or a two elements String
     * array (for enumeration values), an [AnnotationNode], or a [List] of values of one
     * of the preceding types. The list may be null if there is no name value pair.
     */
    val values: List<Any?>

    // ------------------------------------------------------------------------
    // Accept methods
    // ------------------------------------------------------------------------
    /**
     * Checks that this annotation node is compatible with the given ASM API version. This method
     * checks that this node, and all its children recursively, do not contain elements that were
     * introduced in more recent versions of the ASM API than the given version.
     *
     * @param api an ASM API version. Must be one of the `ASM`*x* values in [     ].
     */
    fun check(api: Int) {
        // nothing to do
    }

    /**
     * Makes the given visitor visit this annotation.
     *
     * @param annotationVisitor an annotation visitor. Maybe null.
     */
    fun accept(annotationVisitor: AnnotationVisitor?) {
        if (annotationVisitor == null) return
        var i = 0
        val n = values.size
        while (i < n) {
            val name = values[i] as String?
            val value = values[i + 1]
            accept(annotationVisitor, name, value as Any)
            i += 2
        }
        annotationVisitor.visitEnd()
    }

    companion object {
        /**
         * Makes the given visitor visit a given annotation value.
         *
         * @param annotationVisitor an annotation visitor. Maybe null.
         * @param name the value name.
         * @param value the actual value.
         */
        @Suppress("UNCHECKED_CAST")
        internal fun accept(annotationVisitor: AnnotationVisitor?, name: String?, value: Any) {
            if (annotationVisitor == null) return

            if (value is Array<*> && value.isArrayOf<String>()) {
                value as Array<String>
                annotationVisitor.visitEnum(name, value[0], value[1])
            } else if (value is AnnotationNode) {
                value.accept(annotationVisitor.visitAnnotation(name, value.desc))
            } else if (value is MutableList<*>) {
                annotationVisitor.visitArray(name)?.let { arrayAnnotationVisitor ->
                    var i = 0
                    val n = value.size
                    while (i < n) {
                        accept(arrayAnnotationVisitor, null, value[i] as Any)
                        ++i
                    }
                    arrayAnnotationVisitor.visitEnd()
                }
            } else {
                annotationVisitor.visit(name, value)
            }
        }
    }
}
