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

import org.objectweb.asm.*

/**
 * A node that represents a class.
 *
 * @author Eric Bruneton
 */
class ClassNode(api: Int) : ClassVisitor(api) {
    /**
     * The class version. The minor version is stored in the 16 most significant bits, and the major
     * version in the 16 least significant bits.
     */
    var version: Int = 0

    /**
     * The class's access flags (see [Opcodes]). This field also indicates if
     * the class is deprecated [Opcodes.ACC_DEPRECATED] or a record [Opcodes.ACC_RECORD].
     */
    var access: Int = 0

    /** The internal name of this class (see [org.objectweb.asm.Type.getInternalName]).  */
    var name: String? = null

    /** The signature of this class. May be null.  */
    var signature: String? = null

    /**
     * The internal of name of the super class (see [org.objectweb.asm.Type.getInternalName]).
     * For interfaces, the super class is [Object]. May be null, but only for the
     * [Object] class.
     */
    var superName: String? = null

    /**
     * The internal names of the interfaces directly implemented by this class (see [ ][org.objectweb.asm.Type.getInternalName]).
     */
    var interfaces: MutableList<String?>

    /** The name of the source file from which this class was compiled. May be null.  */
    var sourceFile: String? = null

    /**
     * The correspondence between source and compiled elements of this class. May be null.
     */
    var sourceDebug: String? = null

    /** The module stored in this class. May be null.  */
    var module: ModuleNode? = null

    /**
     * The internal name of the enclosing class of this class (see [ ][org.objectweb.asm.Type.getInternalName]). Must be null if this class is not a
     * local or anonymous class.
     */
    var outerClass: String? = null

    /**
     * The name of the method that contains the class, or null if the class has no
     * enclosing class, or is not enclosed in a method or constructor of its enclosing class (e.g. if
     * it is enclosed in an instance initializer, static initializer, instance variable initializer,
     * or class variable initializer).
     */
    var outerMethod: String? = null

    /**
     * The descriptor of the method that contains the class, or null if the class has no
     * enclosing class, or is not enclosed in a method or constructor of its enclosing class (e.g. if
     * it is enclosed in an instance initializer, static initializer, instance variable initializer,
     * or class variable initializer).
     */
    var outerMethodDesc: String? = null

    /** The runtime visible annotations of this class. May be null.  */
    var visibleAnnotations: MutableList<AnnotationNode?>? = null

    /** The runtime invisible annotations of this class. May be null.  */
    var invisibleAnnotations: MutableList<AnnotationNode?>? = null

    /** The runtime visible type annotations of this class. May be null.  */
    var visibleTypeAnnotations: MutableList<TypeAnnotationNode>? = null

    /** The runtime invisible type annotations of this class. May be null.  */
    var invisibleTypeAnnotations: MutableList<TypeAnnotationNode>? = null

    /** The non standard attributes of this class. May be null.  */
    var attrs: MutableList<Attribute?>? = null

    /** The inner classes of this class.  */
    var innerClasses: MutableList<InnerClassNode?>

    /**
     * The internal name of the nest host class of this class (see [ ][org.objectweb.asm.Type.getInternalName]). May be null.
     */
    var nestHostClass: String? = null

    /**
     * The internal names of the nest members of this class (see [ ][org.objectweb.asm.Type.getInternalName]). May be null.
     */
    var nestMembers: MutableList<String?>? = null

    /**
     * The internal names of the permitted subclasses of this class (see [ ][org.objectweb.asm.Type.getInternalName]). May be null.
     */
    var permittedSubclasses: MutableList<String?>? = null

    /** The record components of this class. May be null.  */
    var recordComponents: MutableList<RecordComponentNode?>? = null

    /** The fields of this class.  */
    var fields: MutableList<FieldNode?>

    /** The methods of this class.  */
    var methods: MutableList<MethodNode?>

    /**
     * Constructs a new [ClassNode]. *Subclasses must not use this constructor*. Instead,
     * they must use the [.ClassNode] version.
     *
     * @throws IllegalStateException If a subclass calls this constructor.
     */
    constructor() : this(Opcodes.ASM9) {
        check(javaClass == ClassNode::class.java)
    }

    /**
     * Constructs a new [ClassNode].
     *
     * @param api the ASM API version implemented by this visitor. Must be one of the `ASM`*x* values in [Opcodes].
     */
    init {
        this.interfaces = ArrayList<String?>()
        this.innerClasses = ArrayList<InnerClassNode?>()
        this.fields = ArrayList<FieldNode?>()
        this.methods = ArrayList<MethodNode?>()
    }

    // -----------------------------------------------------------------------------------------------
    // Implementation of the ClassVisitor abstract class
    // -----------------------------------------------------------------------------------------------
    override fun visit(
        version: Int,
        access: Int,
        name: String?,
        signature: String?,
        superName: String?,
        interfaces: Array<String?>?
    ) {
        this.version = version
        this.access = access
        this.name = name
        this.signature = signature
        this.superName = superName
        this.interfaces = Util.asArrayList<String?>(interfaces)
    }

    override fun visitSource(file: String?, debug: String?) {
        sourceFile = file
        sourceDebug = debug
    }

    override fun visitModule(name: String?, access: Int, version: String?): ModuleVisitor {
        module = MutableModuleNode.Impl(name, access, version)
        return object : ModuleVisitor(Opcodes.ASM9) {
            val impl = module as MutableModuleNode.Impl

            override fun visitMainClass(mainClass: String?) {
                impl.visitMainClass(mainClass)
            }

            override fun visitPackage(packaze: String?) {
                impl.visitPackage(packaze)
            }

            override fun visitRequire(module: String?, access: Int, version: String?) {
                impl.visitRequire(module, access, version)
            }

            override fun visitExport(packaze: String?, access: Int, vararg modules: String?) {
                impl.visitExport(packaze, access, *modules)
            }

            override fun visitOpen(packaze: String?, access: Int, vararg modules: String?) {
                impl.visitOpen(packaze, access, *modules)
            }

            override fun visitUse(service: String?) {
                impl.visitUse(service)
            }

            override fun visitProvide(service: String?, vararg providers: String?) {
                impl.visitProvide(service, *providers)
            }

            override fun visitEnd() {}
        }
    }

    override fun visitNestHost(nestHost: String?) {
        this.nestHostClass = nestHost
    }

    override fun visitOuterClass(owner: String?, name: String?, descriptor: String?) {
        outerClass = owner
        outerMethod = name
        outerMethodDesc = descriptor
    }

    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor {
        val annotation: MutableAnnotationNode = MutableAnnotationNode.Impl(descriptor, mutableListOf())
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

    override fun visitNestMember(nestMember: String?) {
        nestMembers = Util.add<String?>(nestMembers, nestMember)
    }

    override fun visitPermittedSubclass(permittedSubclass: String?) {
        permittedSubclasses = Util.add<String?>(permittedSubclasses, permittedSubclass)
    }

    override fun visitInnerClass(
        name: String?, outerName: String?, innerName: String?, access: Int
    ) {
        val innerClass = InnerClassNode(name, outerName, innerName, access)
        innerClasses.add(innerClass)
    }

    override fun visitRecordComponent(
        name: String?, descriptor: String?, signature: String?
    ): RecordComponentVisitor {
        val recordComponent = MutableRecordComponentNode.Impl(name, descriptor, signature)
        recordComponents = Util.add<RecordComponentNode?>(recordComponents, recordComponent)
        return recordComponent
    }

    override fun visitField(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        value: Any?
    ): FieldVisitor {
        val field = FieldNode(access, name, descriptor, signature, value)
        fields.add(field)
        return field
    }

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<String?>?
    ): MethodVisitor {
        val method = MethodNode(access, name, descriptor, signature, exceptions)
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
