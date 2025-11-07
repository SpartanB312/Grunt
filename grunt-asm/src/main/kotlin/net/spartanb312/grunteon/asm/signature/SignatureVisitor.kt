// ASM: a very small and fast Java bytecode manipulation framework
// Copyright (c) 2000-2011 INRIA, France Telecom
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
// 1. Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright
// notice, this list of conditions and the following disclaimer in the
// documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holders nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
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
package net.spartanb312.grunteon.asm.signature

/**
 * A visitor to visit a generic signature. The methods of this interface must be called in one of
 * the three following orders (the last one is the only valid order for a [SignatureVisitor]
 * that is returned by a method of this interface):
 *
 *
 *  * *ClassSignature* = ( `visitFormalTypeParameter` `visitClassBound`? `visitInterfaceBound`* )* (`visitSuperclass` `visitInterface`* )
 *  * *MethodSignature* = ( `visitFormalTypeParameter` `visitClassBound`? `visitInterfaceBound`* )* (`visitParameterType`* `visitReturnType` `visitExceptionType`* )
 *  * *TypeSignature* = `visitBaseType` | `visitTypeVariable` | `visitArrayType` | ( `visitClassType` `visitTypeArgument`* ( `visitInnerClassType` `visitTypeArgument`* )* `visitEnd` ) )
 *
 *
 * @author Thomas Hallgren
 * @author Eric Bruneton
 * @author Luna
 */
interface SignatureVisitor {

    /**
     * Visits a formal type parameter.
     *
     * @param name the name of the formal parameter.
     */
    fun visitFormalTypeParameter(name: String) {}

    /**
     * Visits the class bound of the last visited formal type parameter.
     *
     * @return a non null visitor to visit the signature of the class bound.
     */
    fun visitClassBound(): SignatureVisitor = this

    /**
     * Visits an interface bound of the last visited formal type parameter.
     *
     * @return a non null visitor to visit the signature of the interface bound.
     */
    fun visitInterfaceBound(): SignatureVisitor = this

    /**
     * Visits the type of the super class.
     *
     * @return a non null visitor to visit the signature of the super class type.
     */
    fun visitSuperclass(): SignatureVisitor = this

    /**
     * Visits the type of an interface implemented by the class.
     *
     * @return a non null visitor to visit the signature of the interface type.
     */
    fun visitInterface(): SignatureVisitor = this

    /**
     * Visits the type of a method parameter.
     *
     * @return a non null visitor to visit the signature of the parameter type.
     */
    fun visitParameterType(): SignatureVisitor = this

    /**
     * Visits the return type of the method.
     *
     * @return a non null visitor to visit the signature of the return type.
     */
    fun visitReturnType(): SignatureVisitor = this

    /**
     * Visits the type of a method exception.
     *
     * @return a non null visitor to visit the signature of the exception type.
     */
    fun visitExceptionType(): SignatureVisitor = this

    /**
     * Visits a signature corresponding to a primitive type.
     *
     * @param descriptor the descriptor of the primitive type, or 'V' for `void` .
     */
    fun visitBaseType(descriptor: Char) {}

    /**
     * Visits a signature corresponding to a type variable.
     *
     * @param name the name of the type variable.
     */
    fun visitTypeVariable(name: String) {}

    /**
     * Visits a signature corresponding to an array type.
     *
     * @return a non null visitor to visit the signature of the array element type.
     */
    fun visitArrayType(): SignatureVisitor = this

    /**
     * Starts the visit of a signature corresponding to a class or interface type.
     *
     * @param name the internal name of the class or interface (see [     ][Type.getInternalName]).
     */
    fun visitClassType(name: String) {}

    /**
     * Visits an inner class.
     *
     * @param name the local name of the inner class in its enclosing class.
     */
    fun visitInnerClassType(name: String) {}

    /** Visits an unbounded type argument of the last visited class or inner class type.  */
    fun visitTypeArgument() {}

    /**
     * Visits a type argument of the last visited class or inner class type.
     *
     * @param wildcard '+', '-' or '='.
     * @return a non null visitor to visit the signature of the type argument.
     */
    fun visitTypeArgument(wildcard: Char): SignatureVisitor = this

    /** Ends the visit of a signature corresponding to a class or interface type.  */
    fun visitEnd() {}

    companion object {
        /** Wildcard for an "extends" type argument.  */
        const val EXTENDS: Char = '+'

        /** Wildcard for a "super" type argument.  */
        const val SUPER: Char = '-'

        /** Wildcard for a normal type argument.  */
        const val INSTANCEOF: Char = '='
    }

    class FromOw2(val ow2: org.objectweb.asm.signature.SignatureVisitor) : SignatureVisitor {
        override fun visitFormalTypeParameter(name: String) {
            ow2.visitFormalTypeParameter(name)
        }

        override fun visitClassBound(): SignatureVisitor {
            return FromOw2(ow2.visitClassBound())
        }

        override fun visitInterfaceBound(): SignatureVisitor {
            return FromOw2(ow2.visitInterfaceBound())
        }

        override fun visitSuperclass(): SignatureVisitor {
            return FromOw2(ow2.visitSuperclass())
        }

        override fun visitInterface(): SignatureVisitor {
            return FromOw2(ow2.visitInterface())
        }

        override fun visitParameterType(): SignatureVisitor {
            return FromOw2(ow2.visitParameterType())
        }

        override fun visitReturnType(): SignatureVisitor {
            return FromOw2(ow2.visitReturnType())
        }

        override fun visitExceptionType(): SignatureVisitor {
            return FromOw2(ow2.visitExceptionType())
        }

        override fun visitBaseType(descriptor: Char) {
            ow2.visitBaseType(descriptor)
        }

        override fun visitTypeVariable(name: String) {
            ow2.visitTypeVariable(name)
        }

        override fun visitArrayType(): SignatureVisitor {
            return FromOw2(ow2.visitArrayType())
        }

        override fun visitClassType(name: String) {
            ow2.visitClassType(name)
        }

        override fun visitInnerClassType(name: String) {
            ow2.visitInnerClassType(name)
        }

        override fun visitTypeArgument() {
            ow2.visitTypeArgument()
        }

        override fun visitTypeArgument(wildcard: Char): SignatureVisitor {
            return FromOw2(ow2.visitTypeArgument(wildcard))
        }

        override fun visitEnd() {
            ow2.visitEnd()
        }
    }

    class ToOw2(val grunt: SignatureVisitor) :
        org.objectweb.asm.signature.SignatureVisitor(org.objectweb.asm.Opcodes.ASM9) {
        override fun visitFormalTypeParameter(name: String) {
            grunt.visitFormalTypeParameter(name)
        }

        override fun visitClassBound(): org.objectweb.asm.signature.SignatureVisitor {
            return ToOw2(grunt.visitClassBound())
        }

        override fun visitInterfaceBound(): org.objectweb.asm.signature.SignatureVisitor {
            return ToOw2(grunt.visitInterfaceBound())
        }

        override fun visitSuperclass(): org.objectweb.asm.signature.SignatureVisitor {
            return ToOw2(grunt.visitSuperclass())
        }

        override fun visitInterface(): org.objectweb.asm.signature.SignatureVisitor {
            return ToOw2(grunt.visitInterface())
        }

        override fun visitParameterType(): org.objectweb.asm.signature.SignatureVisitor {
            return ToOw2(grunt.visitParameterType())
        }

        override fun visitReturnType(): org.objectweb.asm.signature.SignatureVisitor {
            return ToOw2(grunt.visitReturnType())
        }

        override fun visitExceptionType(): org.objectweb.asm.signature.SignatureVisitor {
            return ToOw2(grunt.visitExceptionType())
        }

        override fun visitBaseType(descriptor: Char) {
            grunt.visitBaseType(descriptor)
        }

        override fun visitTypeVariable(name: String) {
            grunt.visitTypeVariable(name)
        }

        override fun visitArrayType(): org.objectweb.asm.signature.SignatureVisitor {
            return ToOw2(grunt.visitArrayType())
        }

        override fun visitClassType(name: String) {
            grunt.visitClassType(name)
        }

        override fun visitInnerClassType(name: String) {
            grunt.visitInnerClassType(name)
        }

        override fun visitTypeArgument() {
            grunt.visitTypeArgument()
        }

        override fun visitTypeArgument(wildcard: Char): org.objectweb.asm.signature.SignatureVisitor {
            return ToOw2(grunt.visitTypeArgument(wildcard))
        }

        override fun visitEnd() {
            grunt.visitEnd()
        }
    }
}
