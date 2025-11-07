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
 * A visitor to visit a Java class. The methods of this class must be called in the following order:
 * `visit` [ `visitSource` ] [ `visitModule` ][ `visitNestHost` ][ `visitOuterClass` ] ( `visitAnnotation` | `visitTypeAnnotation` | `visitAttribute` )* ( `visitNestMember` | [ `* visitPermittedSubclass` ] | `visitInnerClass` | `visitRecordComponent` | `visitField` | `visitMethod` )*
 * `visitEnd`.
 *
 * @author Eric Bruneton
 * @author Luna
 */
interface ClassVisitor {

    /**
     * Visits the header of the class.
     *
     * @param version the class version. The minor version is stored in the 16 most significant bits,
     * and the major version in the 16 least significant bits.
     * @param access the class's access flags (see [Opcodes]). This parameter also indicates if
     * the class is deprecated [Opcodes.ACC_DEPRECATED] or a record [     ][Opcodes.ACC_RECORD].
     * @param name the internal name of the class (see [Type.getInternalName]).
     * @param signature the signature of this class. May be null if the class is not a
     * generic one, and does not extend or implement generic classes or interfaces.
     * @param superName the internal of name of the super class (see [Type.getInternalName]).
     * For interfaces, the super class is [Object]. May be null, but only for the
     * [Object] class.
     * @param interfaces the internal names of the class's interfaces (see [     ][Type.getInternalName]). May be null.
     */
    fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<String>?
    ) {
    }

    /**
     * Visits the source of the class.
     *
     * @param source the name of the source file from which the class was compiled. May be null.
     * @param debug additional debug information to compute the correspondence between source and
     * compiled elements of the class. May be null.
     */
    fun visitSource(source: String?, debug: String?) {}

    /**
     * Visit the module corresponding to the class.
     *
     * @param name the fully qualified name (using dots) of the module.
     * @param access the module access flags, among `ACC_OPEN`, `ACC_SYNTHETIC` and `ACC_MANDATED`.
     * @param version the module version, or null.
     * @return a visitor to visit the module values, or null if this visitor is not
     * interested in visiting this module.
     */
    fun visitModule(name: String, access: Int, version: String?): ModuleVisitor? = null

    /**
     * Visits the nest host class of the class. A nest is a set of classes of the same package that
     * share access to their private members. One of these classes, called the host, lists the other
     * members of the nest, which in turn should link to the host of their nest. This method must be
     * called only once and only if the visited class is a non-host member of a nest. A class is
     * implicitly its own nest, so it's invalid to call this method with the visited class name as
     * argument.
     *
     * @param nestHost the internal name of the host class of the nest (see [     ][Type.getInternalName]).
     */
    fun visitNestHost(nestHost: String) {}

    /**
     * Visits the enclosing class of the class. This method must be called only if this class is a
     * local or anonymous class. See the JVMS 4.7.7 section for more details.
     *
     * @param owner internal name of the enclosing class of the class (see [     ][Type.getInternalName]).
     * @param name the name of the method that contains the class, or null if the class is
     * not enclosed in a method or constructor of its enclosing class (e.g. if it is enclosed in
     * an instance initializer, static initializer, instance variable initializer, or class
     * variable initializer).
     * @param descriptor the descriptor of the method that contains the class, or null if
     * the class is not enclosed in a method or constructor of its enclosing class (e.g. if it is
     * enclosed in an instance initializer, static initializer, instance variable initializer, or
     * class variable initializer).
     */
    fun visitOuterClass(owner: String, name: String?, descriptor: String?) {}

    /**
     * Visits an annotation of the class.
     *
     * @param descriptor the class descriptor of the annotation class.
     * @param visible true if the annotation is visible at runtime.
     * @return a visitor to visit the annotation values, or null if this visitor is not
     * interested in visiting this annotation.
     */
    fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? = null

    /**
     * Visits an annotation on a type in the class signature.
     *
     * @param typeRef a reference to the annotated type. The sort of this type reference must be
     * [TypeReference.CLASS_TYPE_PARAMETER], [     ][TypeReference.CLASS_TYPE_PARAMETER_BOUND] or [TypeReference.CLASS_EXTENDS]. See
     * [TypeReference].
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
     * Visits a non standard attribute of the class.
     *
     * @param attribute an attribute.
     */
    fun visitAttribute(attribute: Attribute) {}

    /**
     * Visits a member of the nest. A nest is a set of classes of the same package that share access
     * to their private members. One of these classes, called the host, lists the other members of the
     * nest, which in turn should link to the host of their nest. This method must be called only if
     * the visited class is the host of a nest. A nest host is implicitly a member of its own nest, so
     * it's invalid to call this method with the visited class name as argument.
     *
     * @param nestMember the internal name of a nest member (see [Type.getInternalName]).
     */
    fun visitNestMember(nestMember: String) {}

    /**
     * Visits a permitted subclasses. A permitted subclass is one of the allowed subclasses of the
     * current class.
     *
     * @param permittedSubclass the internal name of a permitted subclass (see [     ][Type.getInternalName]).
     */
    fun visitPermittedSubclass(permittedSubclass: String) {}

    /**
     * Visits information about an inner class. This inner class is not necessarily a member of the
     * class being visited. More precisely, every class or interface C which is referenced by this
     * class and which is not a package member must be visited with this method. This class must
     * reference its nested class or interface members, and its enclosing class, if any. See the JVMS
     * 4.7.6 section for more details.
     *
     * @param name the internal name of C (see [Type.getInternalName]).
     * @param outerName the internal name of the class or interface C is a member of (see [     ][Type.getInternalName]). Must be null if C is not the member of a class or
     * interface (e.g. for local or anonymous classes).
     * @param innerName the (simple) name of C. Must be null for anonymous inner classes.
     * @param access the access flags of C originally declared in the source code from which this
     * class was compiled.
     */
    fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {}

    /**
     * Visits a record component of the class.
     *
     * @param name the record component name.
     * @param descriptor the record component descriptor (see [Type]).
     * @param signature the record component signature. May be null if the record component
     * type does not use generic types.
     * @return a visitor to visit this record component annotations and attributes, or null
     * if this class visitor is not interested in visiting these annotations and attributes.
     */
    fun visitRecordComponent(
        name: String, descriptor: String, signature: String?
    ): RecordComponentVisitor? = null

    /**
     * Visits a field of the class.
     *
     * @param access the field's access flags (see [Opcodes]). This parameter also indicates if
     * the field is synthetic and/or deprecated.
     * @param name the field's name.
     * @param descriptor the field's descriptor (see [Type]).
     * @param signature the field's signature. May be null if the field's type does not use
     * generic types.
     * @param value the field's initial value. This parameter, which may be null if the
     * field does not have an initial value, must be an [Integer], a [Float], a [     ], a [Double] or a [String] (for `int`, `float`, `long`
     * or `String` fields respectively). *This parameter is only used for static
     * fields*. Its value is ignored for non static fields, which must be initialized through
     * bytecode instructions in constructors or methods.
     * @return a visitor to visit field annotations and attributes, or null if this class
     * visitor is not interested in visiting these annotations and attributes.
     */
    fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?
    ): FieldVisitor? = null

    /**
     * Visits a method of the class. This method *must* return a new [MethodVisitor]
     * instance (or null) each time it is called, i.e., it should not return a previously
     * returned visitor.
     *
     * @param access the method's access flags (see [Opcodes]). This parameter also indicates if
     * the method is synthetic and/or deprecated.
     * @param name the method's name.
     * @param descriptor the method's descriptor (see [Type]).
     * @param signature the method's signature. May be null if the method parameters,
     * return type and exceptions do not use generic types.
     * @param exceptions the internal names of the method's exception classes (see [     ][Type.getInternalName]). May be null.
     * @return an object to visit the byte code of the method, or null if this class
     * visitor is not interested in visiting the code of this method.
     */
    fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<String>?
    ): MethodVisitor? = null

    /**
     * Visits the end of the class. This method, which is the last one to be called, is used to inform
     * the visitor that all the fields and methods of the class have been visited.
     */
    fun visitEnd() {}

    class FromOw2(val ow2: org.objectweb.asm.ClassVisitor) : ClassVisitor {
        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<String>?
        ) {
            ow2.visit(version, access, name, signature, superName, interfaces)
        }

        override fun visitSource(source: String?, debug: String?) {
            ow2.visitSource(source, debug)
        }

        override fun visitModule(
            name: String,
            access: Int,
            version: String?
        ): ModuleVisitor? {
            return ow2.visitModule(name, access, version)?.let { ModuleVisitor.FromOw2(it) }
        }

        override fun visitNestHost(nestHost: String) {
            ow2.visitNestHost(nestHost)
        }

        override fun visitOuterClass(owner: String, name: String?, descriptor: String?) {
            ow2.visitOuterClass(owner, name, descriptor)
        }

        override fun visitAnnotation(
            descriptor: String,
            visible: Boolean
        ): AnnotationVisitor? {
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

        override fun visitNestMember(nestMember: String) {
            ow2.visitNestMember(nestMember)
        }

        override fun visitPermittedSubclass(permittedSubclass: String) {
            ow2.visitPermittedSubclass(permittedSubclass)
        }

        override fun visitInnerClass(
            name: String,
            outerName: String?,
            innerName: String?,
            access: Int
        ) {
            ow2.visitInnerClass(name, outerName, innerName, access)
        }

        override fun visitRecordComponent(
            name: String,
            descriptor: String,
            signature: String?
        ): RecordComponentVisitor? {
            return ow2.visitRecordComponent(name, descriptor, signature)?.let { RecordComponentVisitor.FromOw2(it) }
        }

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?
        ): FieldVisitor? {
            return ow2.visitField(access, name, descriptor, signature, value)?.let { FieldVisitor.FromOw2(it) }
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<String>?
        ): MethodVisitor? {
            return ow2.visitMethod(access, name, descriptor, signature, exceptions)?.let { MethodVisitor.FromOw2(it) }
        }

        override fun visitEnd() {
            ow2.visitEnd()
        }
    }

    class ToOw2(val toOw2: ClassVisitor) : org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<String>?
        ) {
            toOw2.visit(version, access, name, signature, superName, interfaces)
        }

        override fun visitSource(source: String?, debug: String?) {
            toOw2.visitSource(source, debug)
        }

        override fun visitModule(
            name: String,
            access: Int,
            version: String?
        ): org.objectweb.asm.ModuleVisitor? {
            return toOw2.visitModule(name, access, version)?.let { ModuleVisitor.ToOw2(it) }
        }

        override fun visitNestHost(nestHost: String) {
            toOw2.visitNestHost(nestHost)
        }

        override fun visitOuterClass(owner: String, name: String?, descriptor: String?) {
            toOw2.visitOuterClass(owner, name, descriptor)
        }

        override fun visitAnnotation(
            descriptor: String,
            visible: Boolean
        ): org.objectweb.asm.AnnotationVisitor? {
            return toOw2.visitAnnotation(descriptor, visible)?.let { AnnotationVisitor.ToOw2(it) }
        }

        override fun visitTypeAnnotation(
            typeRef: Int,
            typePath: TypePath?,
            descriptor: String,
            visible: Boolean
        ): org.objectweb.asm.AnnotationVisitor? {
            return toOw2.visitTypeAnnotation(typeRef, typePath, descriptor, visible)
                ?.let { AnnotationVisitor.ToOw2(it) }
        }

        override fun visitAttribute(attribute: Attribute) {
            toOw2.visitAttribute(attribute)
        }

        override fun visitNestMember(nestMember: String) {
            toOw2.visitNestMember(nestMember)
        }

        override fun visitPermittedSubclass(permittedSubclass: String) {
            toOw2.visitPermittedSubclass(permittedSubclass)
        }

        override fun visitInnerClass(
            name: String,
            outerName: String?,
            innerName: String?,
            access: Int
        ) {
            toOw2.visitInnerClass(name, outerName, innerName, access)
        }

        override fun visitRecordComponent(
            name: String,
            descriptor: String,
            signature: String?
        ): org.objectweb.asm.RecordComponentVisitor? {
            return toOw2.visitRecordComponent(name, descriptor, signature)?.let { RecordComponentVisitor.ToOw2(it) }
        }

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?
        ): org.objectweb.asm.FieldVisitor? {
            return toOw2.visitField(access, name, descriptor, signature, value)?.let { FieldVisitor.ToOw2(it) }
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<String>
        ): org.objectweb.asm.MethodVisitor? {
            return toOw2.visitMethod(access, name, descriptor, signature, exceptions)?.let { MethodVisitor.ToOw2(it) }
        }

        override fun visitEnd() {
            toOw2.visitEnd()
        }
    }
}
