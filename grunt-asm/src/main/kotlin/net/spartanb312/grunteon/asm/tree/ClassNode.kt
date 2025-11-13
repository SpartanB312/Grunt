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

import net.spartanb312.grunteon.asm.*
import org.objectweb.asm.Attribute
import org.objectweb.asm.TypePath

/**
 * A node that represents a class.
 *
 * @author Eric Bruneton
 * @author Luna
 */
interface ClassNode : Node {
    /**
     * The class version. The minor version is stored in the 16 most significant bits, and the major
     * version in the 16 least significant bits.
     */
    val version: Int

    /**
     * The class's access flags (see [Opcodes]). This field also indicates if
     * the class is deprecated [Opcodes.ACC_DEPRECATED] or a record [Opcodes.ACC_RECORD].
     */
    val access: Int

    /** The internal name of this class (see [org.objectweb.asm.Type.getInternalName]).  */
    val name: String

    /** The signature of this class. May be null.  */
    val signature: String?

    /**
     * The internal of name of the super class (see [org.objectweb.asm.Type.getInternalName]).
     * For interfaces, the super class is [Object]. May be null, but only for the
     * [Object] class.
     */
    val superName: String?

    /**
     * The internal names of the interfaces directly implemented by this class (see [ ][org.objectweb.asm.Type.getInternalName]).
     */
    val interfaces: MutableList<String>

    /** The name of the source file from which this class was compiled. May be null.  */
    val sourceFile: String?

    /**
     * The correspondence between source and compiled elements of this class. May be null.
     */
    val sourceDebug: String?

    /** The module stored in this class. May be null.  */
    val module: ModuleNode?

    /**
     * The internal name of the enclosing class of this class (see [ ][org.objectweb.asm.Type.getInternalName]). Must be null if this class is not a
     * local or anonymous class.
     */
    val outerClass: String?

    /**
     * The name of the method that contains the class, or null if the class has no
     * enclosing class, or is not enclosed in a method or constructor of its enclosing class (e.g. if
     * it is enclosed in an instance initializer, static initializer, instance variable initializer,
     * or class variable initializer).
     */
    val outerMethod: String?

    /**
     * The descriptor of the method that contains the class, or null if the class has no
     * enclosing class, or is not enclosed in a method or constructor of its enclosing class (e.g. if
     * it is enclosed in an instance initializer, static initializer, instance variable initializer,
     * or class variable initializer).
     */
    val outerMethodDesc: String?

    /** The runtime visible annotations of this class. May be null.  */
    val visibleAnnotations: List<AnnotationNode>

    /** The runtime invisible annotations of this class. May be null.  */
    val invisibleAnnotations: List<AnnotationNode>

    /** The runtime visible type annotations of this class. May be null.  */
    val visibleTypeAnnotations: List<TypeAnnotationNode>

    /** The runtime invisible type annotations of this class. May be null.  */
    val invisibleTypeAnnotations: List<TypeAnnotationNode>

    /** The non standard attributes of this class. May be null.  */
    val attrs: List<Attribute>

    /** The inner classes of this class.  */
    val innerClasses: List<InnerClassNode>

    /**
     * The internal name of the nest host class of this class (see [ ][org.objectweb.asm.Type.getInternalName]). May be null.
     */
    val nestHostClass: String?

    /**
     * The internal names of the nest members of this class (see [ ][org.objectweb.asm.Type.getInternalName]). May be null.
     */
    val nestMembers: List<String>

    /**
     * The internal names of the permitted subclasses of this class (see [ ][org.objectweb.asm.Type.getInternalName]). May be null.
     */
    val permittedSubclasses: List<String>

    /** The record components of this class. May be null.  */
    val recordComponents: List<RecordComponentNode>

    /** The fields of this class.  */
    val fields: List<FieldNode>

    /** The methods of this class.  */
    val methods: List<MethodNode>

    /**
     * Makes the given class visitor visit this class.
     *
     * @param classVisitor a class visitor.
     */
    fun accept(classVisitor: ClassVisitor) {
        // Visit the header.
        val interfacesArray = arrayOfNulls<String>(this.interfaces.size)
        for (i in this.interfaces.indices) {
            interfacesArray[i] = this.interfaces[i]
        }
        classVisitor.visit(version, access, name, signature, superName, interfacesArray)
        // Visit the source.
        if (sourceFile != null || sourceDebug != null) {
            classVisitor.visitSource(sourceFile, sourceDebug)
        }
        // Visit the module.
        if (module != null) {
            module!!.accept(classVisitor)
        }
        // Visit the nest host class.
        if (nestHostClass != null) {
            classVisitor.visitNestHost(nestHostClass)
        }
        // Visit the outer class.
        if (outerClass != null) {
            classVisitor.visitOuterClass(outerClass, outerMethod, outerMethodDesc)
        }
        // Visit the annotations.
        if (visibleAnnotations != null) {
            var i = 0
            val n = visibleAnnotations!!.size
            while (i < n) {
                val annotation = visibleAnnotations!!.get(i)
                val av = classVisitor.visitAnnotation(annotation.desc, true)
                val conv = av?.let { net.spartanb312.grunteon.asm.AnnotationVisitor.FromOw2(it) }
                annotation.accept(conv)
                ++i
            }
        }
        if (invisibleAnnotations != null) {
            var i = 0
            val n = invisibleAnnotations!!.size
            while (i < n) {
                val annotation = invisibleAnnotations!!.get(i)
                val av = classVisitor.visitAnnotation(annotation.desc, false)
                val conv = av?.let { net.spartanb312.grunteon.asm.AnnotationVisitor.FromOw2(it) }
                annotation.accept(conv)
                ++i
            }
        }
        if (visibleTypeAnnotations != null) {
            var i = 0
            val n = visibleTypeAnnotations!!.size
            while (i < n) {
                val typeAnnotation: TypeAnnotationNode = visibleTypeAnnotations!!.get(i)
                val av = classVisitor.visitTypeAnnotation(
                    typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, true
                )
                val conv = av?.let { net.spartanb312.grunteon.asm.AnnotationVisitor.FromOw2(it) }
                typeAnnotation.accept(conv)
                ++i
            }
        }
        if (invisibleTypeAnnotations != null) {
            var i = 0
            val n = invisibleTypeAnnotations!!.size
            while (i < n) {
                val typeAnnotation: TypeAnnotationNode = invisibleTypeAnnotations!!.get(i)
                val av = classVisitor.visitTypeAnnotation(
                    typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, false
                )
                val conv = av?.let { net.spartanb312.grunteon.asm.AnnotationVisitor.FromOw2(it) }
                typeAnnotation.accept(conv)
                ++i
            }
        }
        // Visit the non standard attributes.
        if (attrs != null) {
            var i = 0
            val n = attrs!!.size
            while (i < n) {
                classVisitor.visitAttribute(attrs!!.get(i))
                ++i
            }
        }
        // Visit the nest members.
        if (nestMembers != null) {
            var i = 0
            val n = nestMembers!!.size
            while (i < n) {
                classVisitor.visitNestMember(nestMembers!!.get(i))
                ++i
            }
        }
        // Visit the permitted subclasses.
        if (permittedSubclasses != null) {
            var i = 0
            val n = permittedSubclasses!!.size
            while (i < n) {
                classVisitor.visitPermittedSubclass(permittedSubclasses!!.get(i))
                ++i
            }
        }
        // Visit the inner classes.
        run {
            var i = 0
            val n = innerClasses.size
            while (i < n) {
                innerClasses.get(i)!!.accept(classVisitor)
                ++i
            }
        }
        // Visit the record components.
        if (recordComponents != null) {
            var i = 0
            val n = recordComponents!!.size
            while (i < n) {
                recordComponents!!.get(i)!!.accept(classVisitor)
                ++i
            }
        }
        // Visit the fields.
        run {
            var i = 0
            val n = fields.size
            while (i < n) {
                fields.get(i)!!.accept(classVisitor)
                ++i
            }
        }
        // Visit the methods.
        var i = 0
        val n = methods.size
        while (i < n) {
            methods.get(i)!!.accept(classVisitor)
            ++i
        }
        classVisitor.visitEnd()
    }
}

interface MutableClassNode : ClassNode, ClassVisitor {
    override var version: Int
    override var access: Int
    override var name: String
    override var signature: String?
    override var superName: String?
    override val interfaces: MutableList<String>
    override var sourceFile: String?
    override var sourceDebug: String?
    override var module: ModuleNode?
    override var outerClass: String?
    override var outerMethod: String?
    override var outerMethodDesc: String?
    override val visibleAnnotations: MutableList<AnnotationNode>
    override val invisibleAnnotations: MutableList<AnnotationNode>
    override val visibleTypeAnnotations: MutableList<TypeAnnotationNode>
    override val invisibleTypeAnnotations: MutableList<TypeAnnotationNode>
    override val attrs: MutableList<Attribute>
    override var nestHostClass: String?
    override val nestMembers: MutableList<String>
    override val permittedSubclasses: MutableList<String>
    override val innerClasses: MutableList<InnerClassNode>
    override val recordComponents: MutableList<RecordComponentNode>
    override val fields: MutableList<FieldNode>
    override val methods: MutableList<MethodNode>

    // -----------------------------------------------------------------------------------------------
    // Implementation of the ClassVisitor abstract class
    // -----------------------------------------------------------------------------------------------
    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<String>?
    ) {
        this.version = version
        this.access = access
        this.name = name
        this.signature = signature
        this.superName = superName
        this.interfaces.clear()
        interfaces?.let {
            this.interfaces.addAll(it)
        }
    }

    override fun visitSource(source: String?, debug: String?) {
        this.sourceFile = source
        this.sourceDebug = debug
    }

    override fun visitModule(name: String, access: Int, version: String?): ModuleVisitor? {
        val moduleNode = nodeFactory.Module(
            name,
            access,
            version
        )

        return object : ModuleVisitor {
            override fun visitMainClass(mainClass: String) {
                moduleNode.visitMainClass(mainClass)
            }

            override fun visitPackage(packaze: String) {
                moduleNode.visitPackage(packaze)
            }

            override fun visitRequire(module: String, access: Int, version: String?) {
                moduleNode.visitRequire(module, access, version)
            }

            override fun visitExport(
                packaze: String,
                access: Int,
                modules: Array<String>?
            ) {
                moduleNode.visitExport(packaze, access, modules)
            }

            override fun visitOpen(packaze: String, access: Int, modules: Array<String>?) {
                moduleNode.visitOpen(packaze, access, modules)
            }

            override fun visitUse(service: String) {
                moduleNode.visitUse(service)
            }

            override fun visitProvide(service: String, providers: Array<String>) {
                moduleNode.visitProvide(service, providers)
            }

            override fun visitEnd() {
                moduleNode.visitEnd()
                module = moduleNode
            }
        }
    }

    override fun visitNestHost(nestHost: String) {
        this.nestHostClass = nestHost
    }

    override fun visitOuterClass(owner: String, name: String?, descriptor: String?) {
        this.outerClass = owner
        this.outerMethod = name
        this.outerMethodDesc = descriptor
    }

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        val annotation = nodeFactory.Annotation(descriptor)
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
    ): AnnotationVisitor? {
        val typeAnnotation = nodeFactory.TypeAnnotationNode(
            typeRef = typeRef,
            typePath = typePath,
            desc = descriptor
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

    override fun visitNestMember(nestMember: String) {
        nestMembers.add(nestMember)
    }

    override fun visitPermittedSubclass(permittedSubclass: String) {
        permittedSubclasses.add(permittedSubclass)
    }

    override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
        innerClasses.add(nodeFactory.InnerClassNode(name, outerName, innerName, access))
    }

    override fun visitRecordComponent(name: String, descriptor: String, signature: String?): RecordComponentVisitor? {
        val recordComponent = nodeFactory.RecordComponentNode(name, descriptor, signature)
        recordComponents.add(recordComponent)
        return recordComponent
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?
    ): FieldVisitor? {
        val field = nodeFactory.FieldNode(access, name, descriptor, signature, value)
        fields.add(field)
        return field
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: List<String>?
    ): MethodVisitor? {
        val method = nodeFactory.MethodNode(access, name, descriptor, signature, exceptions)
        methods.add(method)
        return method
    }

    override fun visitEnd() {
        // Nothing to do.
    }

    // -----------------------------------------------------------------------------------------------
    // Accept method
    // -----------------------------------------------------------------------------------------------
    /**
     * Checks that this class node is compatible with the given ASM API version. This method checks
     * that this node, and all its children recursively, do not contain elements that were introduced
     * in more recent versions of the ASM API than the given version.
     *
     * @param api an ASM API version. Must be one of the `ASM`*x* values in [     ].
     */
    fun check(api: Int) {
        if (api < Opcodes.ASM9 && permittedSubclasses != null) {
            throw UnsupportedClassVersionException()
        }
        if (api < Opcodes.ASM8 && ((access and Opcodes.ACC_RECORD) != 0 || recordComponents != null)) {
            throw UnsupportedClassVersionException()
        }
        if (api < Opcodes.ASM7 && (nestHostClass != null || nestMembers != null)) {
            throw UnsupportedClassVersionException()
        }
        if (api < Opcodes.ASM6 && module != null) {
            throw UnsupportedClassVersionException()
        }
        if (api < Opcodes.ASM5) {
            if (visibleTypeAnnotations != null && !visibleTypeAnnotations!!.isEmpty()) {
                throw UnsupportedClassVersionException()
            }
            if (invisibleTypeAnnotations != null && !invisibleTypeAnnotations!!.isEmpty()) {
                throw UnsupportedClassVersionException()
            }
        }
        // Check the annotations.
        if (visibleAnnotations != null) {
            for (i in visibleAnnotations!!.indices.reversed()) {
                visibleAnnotations!!.get(i).check(api)
            }
        }
        if (invisibleAnnotations != null) {
            for (i in invisibleAnnotations!!.indices.reversed()) {
                invisibleAnnotations!!.get(i).check(api)
            }
        }
        if (visibleTypeAnnotations != null) {
            for (i in visibleTypeAnnotations!!.indices.reversed()) {
                visibleTypeAnnotations!!.get(i).check(api)
            }
        }
        if (invisibleTypeAnnotations != null) {
            for (i in invisibleTypeAnnotations!!.indices.reversed()) {
                invisibleTypeAnnotations!!.get(i).check(api)
            }
        }
        if (recordComponents != null) {
            for (i in recordComponents!!.indices.reversed()) {
                recordComponents!!.get(i)!!.check(api)
            }
        }
        for (i in fields.indices.reversed()) {
            fields.get(i)!!.check(api)
        }
        for (i in methods.indices.reversed()) {
            methods.get(i)!!.check(api)
        }
    }
}
