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

import net.spartanb312.grunteon.asm.tree.*
import net.spartanb312.grunteon.asm.tree.insn.*
import org.objectweb.asm.*

/**
 * A node that represents a method.
 *
 * @author Eric Bruneton
 */
class MethodNode : MethodVisitor {
    /**
     * The method's access flags (see [Opcodes]). This field also indicates if the method is
     * synthetic and/or deprecated.
     */
    var access: Int = 0

    /** The method's name.  */
    var name: String? = null

    /** The method's descriptor (see [Type]).  */
    var desc: String? = null

    /** The method's signature. May be null.  */
    var signature: String? = null

    /** The internal names of the method's exception classes (see [Type.getInternalName]).  */
    var exceptions: MutableList<String?>? = null

    /** The method parameter info (access flags and name).  */
    var parameters: MutableList<ParameterNode?>? = null

    /** The runtime visible annotations of this method. May be null.  */
    var visibleAnnotations: MutableList<AnnotationNode?>? = null

    /** The runtime invisible annotations of this method. May be null.  */
    var invisibleAnnotations: MutableList<AnnotationNode?>? = null

    /** The runtime visible type annotations of this method. May be null.  */
    var visibleTypeAnnotations: MutableList<TypeAnnotationNode?>? = null

    /** The runtime invisible type annotations of this method. May be null.  */
    var invisibleTypeAnnotations: MutableList<TypeAnnotationNode?>? = null

    /** The non standard attributes of this method. May be null.  */
    var attrs: MutableList<Attribute?>? = null

    /**
     * The default value of this annotation interface method. This field must be a [Byte],
     * [Boolean], [Character], [Short], [Integer], [Long], [ ], [Double], [String] or [Type], or an two elements String array (for
     * enumeration values), a [AnnotationNode], or a [List] of values of one of the
     * preceding types. May be null.
     */
    var annotationDefault: Any? = null

    /**
     * The number of method parameters than can have runtime visible annotations. This number must be
     * less or equal than the number of parameter types in the method descriptor (the default value 0
     * indicates that all the parameters described in the method descriptor can have annotations). It
     * can be strictly less when a method has synthetic parameters and when these parameters are
     * ignored when computing parameter indices for the purpose of parameter annotations (see
     * https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.18).
     */
    var visibleAnnotableParameterCount: Int = 0

    /**
     * The runtime visible parameter annotations of this method. These lists are lists of [ ] objects. May be null.
     */
    var visibleParameterAnnotations: Array<MutableList<AnnotationNode?>?>? = null

    /**
     * The number of method parameters than can have runtime invisible annotations. This number must
     * be less or equal than the number of parameter types in the method descriptor (the default value
     * 0 indicates that all the parameters described in the method descriptor can have annotations).
     * It can be strictly less when a method has synthetic parameters and when these parameters are
     * ignored when computing parameter indices for the purpose of parameter annotations (see
     * https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.18).
     */
    var invisibleAnnotableParameterCount: Int = 0

    /**
     * The runtime invisible parameter annotations of this method. These lists are lists of [ ] objects. May be null.
     */
    var invisibleParameterAnnotations: Array<MutableList<AnnotationNode?>?>? = null

    /** The instructions of this method.  */
    var instructions: InsnList

    /** The try catch blocks of this method.  */
    var tryCatchBlocks: MutableList<TryCatchBlockNode>? = null

    /** The maximum stack size of this method.  */
    var maxStack: Int = 0

    /** The maximum number of local variables of this method.  */
    var maxLocals: Int = 0

    /** The local variables of this method. May be null  */
    var localVariables: MutableList<LocalVariableNode?>? = null

    /** The visible local variable annotations of this method. May be null  */
    var visibleLocalVariableAnnotations: MutableList<LocalVariableAnnotationNode?>? = null

    /** The invisible local variable annotations of this method. May be null  */
    var invisibleLocalVariableAnnotations: MutableList<LocalVariableAnnotationNode?>? = null

    /** Whether the accept method has been called on this object.  */
    private var visited = false

    /**
     * Constructs an uninitialized [MethodNode]. *Subclasses must not use this
     * constructor*. Instead, they must use the [.MethodNode] version.
     *
     * @throws IllegalStateException If a subclass calls this constructor.
     */
    constructor() : this( /* latest api = */Opcodes.ASM9) {
        check(javaClass == MethodNode::class.java)
    }

    /**
     * Constructs an uninitialized [MethodNode].
     *
     * @param api the ASM API version implemented by this visitor. Must be one of the `ASM`*x* values in [Opcodes].
     */
    constructor(api: Int) : super(api) {
        this.instructions = net.spartanb312.grunteon.asm.tree.InsnList()
    }

    /**
     * Constructs a new [MethodNode]. *Subclasses must not use this constructor*. Instead,
     * they must use the [.MethodNode] version.
     *
     * @param access the method's access flags (see [Opcodes]). This parameter also indicates if
     * the method is synthetic and/or deprecated.
     * @param name the method's name.
     * @param descriptor the method's descriptor (see [Type]).
     * @param signature the method's signature. May be null.
     * @param exceptions the internal names of the method's exception classes (see [     ][Type.getInternalName]). May be null.
     * @throws IllegalStateException If a subclass calls this constructor.
     */
    constructor(
        access: Int,
        name: String?,
        descriptor: String,
        signature: String?,
        exceptions: Array<String?>?
    ) : this( /* latest api = */Opcodes.ASM9, access, name, descriptor, signature, exceptions) {
        check(javaClass == MethodNode::class.java)
    }

    /**
     * Constructs a new [MethodNode].
     *
     * @param api the ASM API version implemented by this visitor. Must be one of the `ASM`*x* values in [Opcodes].
     * @param access the method's access flags (see [Opcodes]). This parameter also indicates if
     * the method is synthetic and/or deprecated.
     * @param name the method's name.
     * @param descriptor the method's descriptor (see [Type]).
     * @param signature the method's signature. May be null.
     * @param exceptions the internal names of the method's exception classes (see [     ][Type.getInternalName]). May be null.
     */
    constructor(
        api: Int,
        access: Int,
        name: String?,
        descriptor: String,
        signature: String?,
        exceptions: Array<String?>?
    ) : super(api) {
        this.access = access
        this.name = name
        this.desc = descriptor
        this.signature = signature
        this.exceptions = Util.asArrayList<String?>(exceptions)
        if ((access and Opcodes.ACC_ABSTRACT) == 0) {
            this.localVariables = ArrayList<LocalVariableNode?>(5)
        }
        this.tryCatchBlocks = ArrayList<TryCatchBlockNode>()
        this.instructions = net.spartanb312.grunteon.asm.tree.InsnList()
    }

    // -----------------------------------------------------------------------------------------------
    // Implementation of the MethodVisitor abstract class
    // -----------------------------------------------------------------------------------------------
    override fun visitParameter(name: String?, access: Int) {
        if (parameters == null) {
            parameters = ArrayList<ParameterNode?>(5)
        }
        parameters!!.add(net.spartanb312.grunteon.asm.tree.ParameterNode(name, access))
    }

    override fun visitAnnotationDefault(): AnnotationVisitor? {
        val list = object : ArrayList<Any?>(0) {
            override fun add(o: Any?): Boolean {
                annotationDefault = o
                return super.add(o)
            }
        }
        return MutableAnnotationNode.Impl(null, list)
    }

    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
        return MutableAnnotationNode.Impl(descriptor, mutableListOf())
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
        typeRef: Int,
        typePath: TypePath?,
        descriptor: String?,
        visible: Boolean
    ): AnnotationVisitor? {
        return net.spartanb312.grunteon.asm.tree.TypeAnnotationNode(typeRef, typePath, descriptor)
    }

    override fun visitAnnotableParameterCount(parameterCount: Int, visible: Boolean) {
        if (visible) {
            visibleAnnotableParameterCount = parameterCount
        } else {
            invisibleAnnotableParameterCount = parameterCount
        }
    }

    override fun visitParameterAnnotation(
        parameter: Int, descriptor: String?, visible: Boolean
    ): AnnotationVisitor {
        val annotation: MutableAnnotationNode = MutableAnnotationNode.Impl(descriptor, mutableListOf())
        if (visible) {
            visibleParameterAnnotations = Util.setOrCreate(visibleParameterAnnotations, annotation)
        } else {
            invisibleParameterAnnotations = Util.setOrCreate(invisibleParameterAnnotations, annotation)
        }
        return annotation
    }

    override fun visitAttribute(attribute: Attribute?) {
        attrs = Util.add<Attribute?>(attrs, attribute)
    }

    override fun visitCode() {
        // Nothing to do.
    }

    override fun visitFrame(
        type: Int,
        numLocal: Int,
        local: Array<Any?>?,
        numStack: Int,
        stack: Array<Any?>?
    ) {
        instructions.add(
            FrameNode(
                type,
                numLocal,
                if (local == null) null else getLabelNodes(local),
                numStack,
                if (stack == null) null else getLabelNodes(stack)
            )
        )
    }

    override fun visitInsn(opcode: Int) {
        instructions.add(net.spartanb312.grunteon.asm.tree.insn.InsnNode(opcode))
    }

    override fun visitIntInsn(opcode: Int, operand: Int) {
        instructions.add(net.spartanb312.grunteon.asm.tree.insn.IntInsnNode(opcode, operand))
    }

    override fun visitVarInsn(opcode: Int, varIndex: Int) {
        instructions.add(net.spartanb312.grunteon.asm.tree.insn.VarInsnNode(opcode, varIndex))
    }

    override fun visitTypeInsn(opcode: Int, type: String?) {
        instructions.add(net.spartanb312.grunteon.asm.tree.insn.TypeInsnNode(opcode, type))
    }

    override fun visitFieldInsn(
        opcode: Int, owner: String?, name: String?, descriptor: String?
    ) {
        instructions.add(FieldInsnNode(opcode, owner, name, descriptor))
    }

    override fun visitMethodInsn(
        opcodeAndSource: Int,
        owner: String?,
        name: String?,
        descriptor: String?,
        isInterface: Boolean
    ) {
        if (api < Opcodes.ASM5 && (opcodeAndSource and Opcodes.SOURCE_DEPRECATED) == 0) {
            // Redirect the call to the deprecated version of this method.
            super.visitMethodInsn(opcodeAndSource, owner, name, descriptor, isInterface)
            return
        }
        val opcode = opcodeAndSource and Opcodes.SOURCE_MASK.inv()

        instructions.add(
            net.spartanb312.grunteon.asm.tree.insn.IMethodInsnNode(
                opcode,
                owner,
                name,
                descriptor,
                isInterface
            )
        )
    }

    override fun visitInvokeDynamicInsn(
        name: String?,
        descriptor: String?,
        bootstrapMethodHandle: Handle?,
        vararg bootstrapMethodArguments: Any?
    ) {
        instructions.add(
            net.spartanb312.grunteon.asm.tree.insn.InvokeDynamicInsnNode(
                name, descriptor, bootstrapMethodHandle, *bootstrapMethodArguments
            )
        )
    }

    override fun visitJumpInsn(opcode: Int, label: Label) {
        instructions.add(net.spartanb312.grunteon.asm.tree.insn.JumpInsnNode(opcode, getLabelNode(label)))
    }

    override fun visitLabel(label: Label) {
        instructions.add(getLabelNode(label))
    }

    override fun visitLdcInsn(value: Any?) {
        instructions.add(net.spartanb312.grunteon.asm.tree.insn.LdcInsnNode(value))
    }

    override fun visitIincInsn(varIndex: Int, increment: Int) {
        instructions.add(net.spartanb312.grunteon.asm.tree.insn.IincInsnNode(varIndex, increment))
    }

    override fun visitTableSwitchInsn(
        min: Int, max: Int, dflt: Label, vararg labels: Label?
    ) {
        instructions.add(
            net.spartanb312.grunteon.asm.tree.insn.TableSwitchInsnNode(
                min,
                max,
                getLabelNode(dflt),
                *getLabelNodes(labels)
            )
        )
    }

    override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray?, labels: Array<Label?>) {
        instructions.add(
            net.spartanb312.grunteon.asm.tree.insn.LookupSwitchInsnNode(
                getLabelNode(dflt),
                keys,
                getLabelNodes(labels)
            )
        )
    }

    override fun visitMultiANewArrayInsn(descriptor: String?, numDimensions: Int) {
        instructions.add(net.spartanb312.grunteon.asm.tree.insn.MultiANewArrayInsnNode(descriptor, numDimensions))
    }

    override fun visitInsnAnnotation(
        typeRef: Int, typePath: TypePath?, descriptor: String?, visible: Boolean
    ): AnnotationVisitor {
        // Find the last real instruction, i.e. the instruction targeted by this annotation.
        var currentInsn = instructions.getLast()
        while (currentInsn.getOpcode() == -1) {
            currentInsn = currentInsn.getPrevious()
        }
        // Add the annotation to this instruction.
        val typeAnnotation: TypeAnnotationNode =
            net.spartanb312.grunteon.asm.tree.TypeAnnotationNode(typeRef, typePath, descriptor)
        if (visible) {
            currentInsn.visibleTypeAnnotations =
                Util.add<TypeAnnotationNode?>(currentInsn.visibleTypeAnnotations, typeAnnotation)
        } else {
            currentInsn.invisibleTypeAnnotations =
                Util.add<TypeAnnotationNode?>(currentInsn.invisibleTypeAnnotations, typeAnnotation)
        }
        return typeAnnotation
    }

    override fun visitTryCatchBlock(
        start: Label, end: Label, handler: Label, type: String?
    ) {
        val tryCatchBlock =
            TryCatchBlockNode(getLabelNode(start), getLabelNode(end), getLabelNode(handler), type)
        tryCatchBlocks = Util.add<TryCatchBlockNode?>(tryCatchBlocks, tryCatchBlock)
    }

    override fun visitTryCatchAnnotation(
        typeRef: Int, typePath: TypePath?, descriptor: String?, visible: Boolean
    ): AnnotationVisitor {
        val tryCatchBlock = tryCatchBlocks!!.get((typeRef and 0x00FFFF00) shr 8)
        val typeAnnotation: TypeAnnotationNode =
            net.spartanb312.grunteon.asm.tree.TypeAnnotationNode(typeRef, typePath, descriptor)
        if (visible) {
            tryCatchBlock.visibleTypeAnnotations =
                Util.add<TypeAnnotationNode?>(tryCatchBlock.visibleTypeAnnotations, typeAnnotation)
        } else {
            tryCatchBlock.invisibleTypeAnnotations =
                Util.add<TypeAnnotationNode?>(tryCatchBlock.invisibleTypeAnnotations, typeAnnotation)
        }
        return typeAnnotation
    }

    override fun visitLocalVariable(
        name: String?,
        descriptor: String?,
        signature: String?,
        start: Label,
        end: Label,
        index: Int
    ) {
        val localVariable =
            LocalVariableNode(
                name, descriptor, signature, getLabelNode(start), getLabelNode(end), index
            )
        localVariables = Util.add<LocalVariableNode?>(localVariables, localVariable)
    }

    override fun visitLocalVariableAnnotation(
        typeRef: Int,
        typePath: TypePath?,
        start: Array<Label?>,
        end: Array<Label?>,
        index: IntArray?,
        descriptor: String?,
        visible: Boolean
    ): AnnotationVisitor {
        val localVariableAnnotation =
            LocalVariableAnnotationNode(
                typeRef, typePath, getLabelNodes(start), getLabelNodes(end), index, descriptor
            )
        if (visible) {
            visibleLocalVariableAnnotations =
                Util.add<LocalVariableAnnotationNode?>(visibleLocalVariableAnnotations, localVariableAnnotation)
        } else {
            invisibleLocalVariableAnnotations =
                Util.add<LocalVariableAnnotationNode?>(invisibleLocalVariableAnnotations, localVariableAnnotation)
        }
        return localVariableAnnotation
    }

    override fun visitLineNumber(line: Int, start: Label) {
        instructions.add(LineNumberNode(line, getLabelNode(start)))
    }

    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        this.maxStack = maxStack
        this.maxLocals = maxLocals
    }

    override fun visitEnd() {
        // Nothing to do.
    }

    /**
     * Returns the LabelNode corresponding to the given Label. Creates a new LabelNode if necessary.
     * The default implementation of this method uses the [Label.info] field to store
     * associations between labels and label nodes.
     *
     * @param label a Label.
     * @return the LabelNode corresponding to label.
     */
    protected fun getLabelNode(label: Label): LabelNode {
        if (label.info !is LabelNode) {
            label.info = net.spartanb312.grunteon.asm.tree.insn.LabelNode()
        }
        return label.info as LabelNode
    }

    private fun getLabelNodes(labels: Array<Label?>): Array<LabelNode?> {
        val labelNodes = arrayOfNulls<LabelNode>(labels.size)
        var i = 0
        val n = labels.size
        while (i < n) {
            labelNodes[i] = getLabelNode(labels[i]!!)
            ++i
        }
        return labelNodes
    }

    private fun getLabelNodes(objects: Array<Any?>): Array<Any?> {
        val labelNodes = arrayOfNulls<Any>(objects.size)
        var i = 0
        val n = objects.size
        while (i < n) {
            var o = objects[i]
            if (o is Label) {
                o = getLabelNode(o)
            }
            labelNodes[i] = o
            ++i
        }
        return labelNodes
    }

    // -----------------------------------------------------------------------------------------------
    // Accept method
    // -----------------------------------------------------------------------------------------------
    /**
     * Checks that this method node is compatible with the given ASM API version. This method checks
     * that this node, and all its children recursively, do not contain elements that were introduced
     * in more recent versions of the ASM API than the given version.
     *
     * @param api an ASM API version. Must be one of the `ASM`*x* values in [     ].
     */
    fun check(api: Int) {
        if (api == Opcodes.ASM4) {
            if (parameters != null && !parameters!!.isEmpty()) {
                throw UnsupportedClassVersionException()
            }
            if (visibleTypeAnnotations != null && !visibleTypeAnnotations!!.isEmpty()) {
                throw UnsupportedClassVersionException()
            }
            if (invisibleTypeAnnotations != null && !invisibleTypeAnnotations!!.isEmpty()) {
                throw UnsupportedClassVersionException()
            }
            if (tryCatchBlocks != null) {
                for (i in tryCatchBlocks!!.indices.reversed()) {
                    val tryCatchBlock = tryCatchBlocks!!.get(i)
                    if (tryCatchBlock.visibleTypeAnnotations != null
                        && !tryCatchBlock.visibleTypeAnnotations.isEmpty()
                    ) {
                        throw UnsupportedClassVersionException()
                    }
                    if (tryCatchBlock.invisibleTypeAnnotations != null
                        && !tryCatchBlock.invisibleTypeAnnotations.isEmpty()
                    ) {
                        throw UnsupportedClassVersionException()
                    }
                }
            }
            for (i in instructions.size() - 1 downTo 0) {
                val insn = instructions.get(i)
                if (insn.visibleTypeAnnotations != null && !insn.visibleTypeAnnotations.isEmpty()) {
                    throw UnsupportedClassVersionException()
                }
                if (insn.invisibleTypeAnnotations != null && !insn.invisibleTypeAnnotations.isEmpty()) {
                    throw UnsupportedClassVersionException()
                }
                if (insn is IMethodInsnNode) {
                    val isInterface = insn.itf
                    if (isInterface != (insn.opcode == Opcodes.INVOKEINTERFACE)) {
                        throw UnsupportedClassVersionException()
                    }
                } else if (insn is InvokeDynamicInsnNode) {
                    throw UnsupportedClassVersionException()
                } else if (insn is LdcInsnNode) {
                    val value = insn.constant
                    if (value is Handle
                        || (value is Type && value.getSort() == Type.METHOD)
                    ) {
                        throw UnsupportedClassVersionException()
                    }
                }
            }
            if (visibleLocalVariableAnnotations != null && !visibleLocalVariableAnnotations!!.isEmpty()) {
                throw UnsupportedClassVersionException()
            }
            if (invisibleLocalVariableAnnotations != null
                && !invisibleLocalVariableAnnotations!!.isEmpty()
            ) {
                throw UnsupportedClassVersionException()
            }
        }
        if (api < Opcodes.ASM7) {
            for (i in instructions.size() - 1 downTo 0) {
                val insn = instructions.get(i)
                if (insn is LdcInsnNode) {
                    val value = insn.constant
                    if (value is ConstantDynamic) {
                        throw UnsupportedClassVersionException()
                    }
                }
            }
        }
    }

    /**
     * Makes the given class visitor visit this method.
     *
     * @param classVisitor a class visitor.
     */
    fun accept(classVisitor: ClassVisitor) {
        val exceptionsArray = if (exceptions == null) null else exceptions.toTypedArray<String?>()
        val methodVisitor =
            classVisitor.visitMethod(access, name, desc, signature, exceptionsArray)
        if (methodVisitor != null) {
            accept(methodVisitor)
        }
    }

    /**
     * Makes the given method visitor visit this method.
     *
     * @param methodVisitor a method visitor.
     */
    fun accept(methodVisitor: MethodVisitor) {
        // Visit the parameters.
        if (parameters != null) {
            var i = 0
            val n = parameters!!.size
            while (i < n) {
                parameters!!.get(i)!!.accept(methodVisitor)
                i++
            }
        }
        // Visit the annotations.
        if (annotationDefault != null) {
            val av = methodVisitor.visitAnnotationDefault()
            val conv = av?.let { net.spartanb312.grunteon.asm.AnnotationVisitor.FromOw2(it) }
            AnnotationNode.Companion.accept(conv, null, annotationDefault!!)
            if (av != null) {
                av.visitEnd()
            }
        }
        if (visibleAnnotations != null) {
            var i = 0
            val n = visibleAnnotations!!.size
            while (i < n) {
                val annotation = visibleAnnotations!!.get(i)
                val av = methodVisitor.visitAnnotation(annotation.desc, true)
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
                val av = methodVisitor.visitAnnotation(annotation.desc, false)
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
                val av = methodVisitor.visitTypeAnnotation(
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
                val av = methodVisitor.visitTypeAnnotation(
                    typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, false
                )
                val conv = av?.let { net.spartanb312.grunteon.asm.AnnotationVisitor.FromOw2(it) }
                typeAnnotation.accept(conv)
                ++i
            }
        }
        if (visibleAnnotableParameterCount > 0) {
            methodVisitor.visitAnnotableParameterCount(visibleAnnotableParameterCount, true)
        }
        if (visibleParameterAnnotations != null) {
            var i = 0
            val n = visibleParameterAnnotations!!.size
            while (i < n) {
                val parameterAnnotations = visibleParameterAnnotations!![i]
                if (parameterAnnotations == null) {
                    ++i
                    continue
                }
                var j = 0
                val m = parameterAnnotations.size
                while (j < m) {
                    val annotation = parameterAnnotations.get(j)
                    val av = methodVisitor.visitParameterAnnotation(i, annotation.desc, true)
                    val conv = av?.let { net.spartanb312.grunteon.asm.AnnotationVisitor.FromOw2(it) }
                    annotation.accept(conv)
                    ++j
                }
                ++i
            }
        }
        if (invisibleAnnotableParameterCount > 0) {
            methodVisitor.visitAnnotableParameterCount(invisibleAnnotableParameterCount, false)
        }
        if (invisibleParameterAnnotations != null) {
            var i = 0
            val n = invisibleParameterAnnotations!!.size
            while (i < n) {
                val parameterAnnotations = invisibleParameterAnnotations!![i]
                if (parameterAnnotations == null) {
                    ++i
                    continue
                }
                var j = 0
                val m = parameterAnnotations.size
                while (j < m) {
                    val annotation = parameterAnnotations.get(j)
                    val av = methodVisitor.visitParameterAnnotation(i, annotation.desc, false)
                    val conv = av?.let { net.spartanb312.grunteon.asm.AnnotationVisitor.FromOw2(it) }
                    annotation.accept(conv)
                    ++j
                }
                ++i
            }
        }
        // Visit the non standard attributes.
        if (visited) {
            instructions.resetLabels()
        }
        if (attrs != null) {
            var i = 0
            val n = attrs!!.size
            while (i < n) {
                methodVisitor.visitAttribute(attrs!!.get(i))
                ++i
            }
        }
        // Visit the code.
        if (instructions.size() > 0) {
            methodVisitor.visitCode()
            // Visits the try catch blocks.
            if (tryCatchBlocks != null) {
                var i = 0
                val n = tryCatchBlocks!!.size
                while (i < n) {
                    tryCatchBlocks!!.get(i).updateIndex(i)
                    tryCatchBlocks!!.get(i).accept(methodVisitor)
                    ++i
                }
            }
            // Visit the instructions.
            instructions.accept(methodVisitor)
            // Visits the local variables.
            if (localVariables != null) {
                var i = 0
                val n = localVariables!!.size
                while (i < n) {
                    localVariables!!.get(i)!!.accept(methodVisitor)
                    ++i
                }
            }
            // Visits the local variable annotations.
            if (visibleLocalVariableAnnotations != null) {
                var i = 0
                val n = visibleLocalVariableAnnotations!!.size
                while (i < n) {
                    visibleLocalVariableAnnotations!!.get(i)!!.accept(methodVisitor, true)
                    ++i
                }
            }
            if (invisibleLocalVariableAnnotations != null) {
                var i = 0
                val n = invisibleLocalVariableAnnotations!!.size
                while (i < n) {
                    invisibleLocalVariableAnnotations!!.get(i)!!.accept(methodVisitor, false)
                    ++i
                }
            }
            methodVisitor.visitMaxs(maxStack, maxLocals)
            visited = true
        }
        methodVisitor.visitEnd()
    }
}
