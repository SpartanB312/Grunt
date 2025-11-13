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
import net.spartanb312.grunteon.asm.MethodVisitor
import net.spartanb312.grunteon.asm.tree.insn.LabelNode
import net.spartanb312.grunteon.asm.tree.insn.NewArrayInsnNode
import org.objectweb.asm.Attribute
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.TypePath

/**
 * A node that represents a method.
 *
 * @author Eric Bruneton
 * @author Luna
 */
interface MethodNode : Node {
    /**
     * The method's access flags (see [Opcodes]). This field also indicates if the method is
     * synthetic and/or deprecated.
     */
    val access: Int

    /** The method's name.  */
    val name: String

    /** The method's descriptor (see [Type]).  */
    val desc: String

    /** The method's signature. May be null.  */
    val signature: String?

    /** The internal names of the method's exception classes (see [Type.getInternalName]).  */
    val exceptions: List<String>

    /** The method parameter info (access flags and name).  */
    val parameters: List<ParameterNode>

    /** The runtime visible annotations of this method. May be null.  */
    val visibleAnnotations: List<AnnotationNode>

    /** The runtime invisible annotations of this method. May be null.  */
    val invisibleAnnotations: List<AnnotationNode>

    /** The runtime visible type annotations of this method. May be null.  */
    val visibleTypeAnnotations: List<TypeAnnotationNode>

    /** The runtime invisible type annotations of this method. May be null.  */
    val invisibleTypeAnnotations: List<TypeAnnotationNode>

    /** The non standard attributes of this method. May be null.  */
    val attrs: List<Attribute>

    /**
     * The default value of this annotation interface method. This field must be a [Byte],
     * [Boolean], [Character], [Short], [Integer], [Long], [ ], [Double], [String] or [Type], or an two elements String array (for
     * enumeration values), a [AnnotationNode], or a [List] of values of one of the
     * preceding types. May be null.
     */
    val annotationDefault: Any?

    /**
     * The number of method parameters than can have runtime visible annotations. This number must be
     * less or equal than the number of parameter types in the method descriptor (the default value 0
     * indicates that all the parameters described in the method descriptor can have annotations). It
     * can be strictly less when a method has synthetic parameters and when these parameters are
     * ignored when computing parameter indices for the purpose of parameter annotations (see
     * https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.18).
     */
    val visibleAnnotableParameterCount: Int

    /**
     * The runtime visible parameter annotations of this method. These lists are lists of [ ] objects. May be null.
     */
    val visibleParameterAnnotations: List<List<AnnotationNode>>

    /**
     * The number of method parameters than can have runtime invisible annotations. This number must
     * be less or equal than the number of parameter types in the method descriptor (the default value
     * 0 indicates that all the parameters described in the method descriptor can have annotations).
     * It can be strictly less when a method has synthetic parameters and when these parameters are
     * ignored when computing parameter indices for the purpose of parameter annotations (see
     * https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.18).
     */
    val invisibleAnnotableParameterCount: Int

    /**
     * The runtime invisible parameter annotations of this method. These lists are lists of [ ] objects. May be null.
     */
    val invisibleParameterAnnotations: List<List<AnnotationNode>>

    /** The instructions of this method.  */
    val instructions: InsnList

    /** The try catch blocks of this method.  */
    val tryCatchBlocks: List<TryCatchBlockNode>

    /** The maximum stack size of this method.  */
    val maxStack: Int

    /** The maximum number of local variables of this method.  */
    val maxLocals: Int

    /** The local variables of this method. May be null  */
    val localVariables: List<LocalVariableNode>

    /** The visible local variable annotations of this method. May be null  */
    val visibleLocalVariableAnnotations: List<LocalVariableAnnotationNode>

    /** The invisible local variable annotations of this method. May be null  */
    val invisibleLocalVariableAnnotations: List<LocalVariableAnnotationNode>

    // -----------------------------------------------------------------------------------------------
    // Accept method
    // -----------------------------------------------------------------------------------------------

    /**
     * Makes the given class visitor visit this method.
     *
     * @param classVisitor a class visitor.
     */
    fun accept(classVisitor: ClassVisitor) {
        val methodVisitor =
            classVisitor.visitMethod(access, name, desc, signature, exceptions.takeIf { it.isNotEmpty() })
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
        parameters.forEach {
            it.accept(methodVisitor)
        }
        // Visit the annotations.
        annotationDefault?.let { node ->
            methodVisitor.visitAnnotationDefault()?.let { visitor ->
                AnnotationNode.accept(visitor, "", node)
                visitor.visitEnd()
            }
        }
        visibleAnnotations.forEach {
            it.accept(methodVisitor.visitAnnotation(it.desc, true))
        }
        invisibleAnnotations.forEach {
            it.accept(methodVisitor.visitAnnotation(it.desc, false))
        }
        visibleTypeAnnotations.forEach {
            it.accept(methodVisitor.visitTypeAnnotation(it.typeRef, it.typePath, it.desc, true))
        }
        invisibleTypeAnnotations.forEach {
            it.accept(methodVisitor.visitTypeAnnotation(it.typeRef, it.typePath, it.desc, false))
        }
        if (visibleAnnotableParameterCount > 0) {
            methodVisitor.visitAnnotableParameterCount(visibleAnnotableParameterCount, true)
        }
        visibleParameterAnnotations.forEachIndexed { i, parameterAnnotations ->
            parameterAnnotations.forEach { annotation ->
                annotation.accept(methodVisitor.visitParameterAnnotation(i, annotation.desc, true))
            }
        }
        if (invisibleAnnotableParameterCount > 0) {
            methodVisitor.visitAnnotableParameterCount(invisibleAnnotableParameterCount, false)
        }
        invisibleParameterAnnotations.forEachIndexed { i, parameterAnnotations ->
            parameterAnnotations.forEach { annotation ->
                annotation.accept(methodVisitor.visitParameterAnnotation(i, annotation.desc, false))
            }
        }
        // Visit the non standard attributes.
        attrs.forEach {
            methodVisitor.visitAttribute(it)
        }
        // Visit the code.
        if (instructions.isNotEmpty()) {
            methodVisitor.visitCode()
            tryCatchBlocks.forEach {
                it.accept(methodVisitor)
            }
            instructions.accept(methodVisitor)
            localVariables.forEach {
                it.accept(methodVisitor)
            }
            visibleLocalVariableAnnotations.forEach {
                it.accept(methodVisitor, true)
            }
            invisibleLocalVariableAnnotations.forEach {
                it.accept(methodVisitor, false)
            }
            methodVisitor.visitMaxs(maxStack, maxLocals)
        }
        methodVisitor.visitEnd()
    }
}

interface MutableMethodNode : MethodNode, MethodVisitor {
    override var access: Int
    override var name: String
    override var desc: String
    override var signature: String?
    override val exceptions: MutableList<String>
    override val parameters: MutableList<ParameterNode>
    override val visibleAnnotations: MutableList<AnnotationNode>
    override val invisibleAnnotations: MutableList<AnnotationNode>
    override val visibleTypeAnnotations: MutableList<TypeAnnotationNode>
    override val invisibleTypeAnnotations: MutableList<TypeAnnotationNode>
    override val attrs: MutableList<Attribute>
    override var annotationDefault: Any?
    override var visibleAnnotableParameterCount: Int
    override val visibleParameterAnnotations: MutableList<MutableList<AnnotationNode>>
    override var invisibleAnnotableParameterCount: Int
    override val invisibleParameterAnnotations: MutableList<MutableList<AnnotationNode>>
    override val instructions: MutableInsnList
    override val tryCatchBlocks: MutableList<MutableTryCatchBlockNode>
    override var maxStack: Int
    override var maxLocals: Int
    override val localVariables: MutableList<MutableLocalVariableNode>
    override val visibleLocalVariableAnnotations: MutableList<LocalVariableAnnotationNode>
    override val invisibleLocalVariableAnnotations: MutableList<LocalVariableAnnotationNode>

    override fun getLabelNode(label: Label): LabelNode {
        var info = label.info
        if (info !is LabelNode) {
            info = instructions.createLabelNode()
            label.info = info
        }
        return info
    }

    override fun getLabel(labelNode: LabelNode): Label {
        throw UnsupportedOperationException()
    }


    // -----------------------------------------------------------------------------------------------
    // Implementation of the MethodVisitor abstract class
    // -----------------------------------------------------------------------------------------------
    override fun visitParameter(name: String?, access: Int) {
        parameters.add(nodeFactory.ParameterNode(name!!, access))
    }

    override fun visitAnnotationDefault(): AnnotationVisitor? {
        val list = object : ArrayList<Any>(0) {
            override fun add(o: Any): Boolean {
                annotationDefault = o
                return super.add(o)
            }
        }
        return nodeFactory.Annotation(values = list)
    }

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        val annotation = nodeFactory.Annotation(descriptor, mutableListOf())
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
        val typeAnnotation = nodeFactory.TypeAnnotationNode(typeRef = typeRef, typePath = typePath, desc = descriptor)
        if (visible) {
            visibleTypeAnnotations.add(typeAnnotation)
        } else {
            invisibleTypeAnnotations.add(typeAnnotation)
        }
        return typeAnnotation
    }

    override fun visitAnnotableParameterCount(parameterCount: Int, visible: Boolean) {
        if (visible) {
            visibleAnnotableParameterCount = parameterCount
        } else {
            invisibleAnnotableParameterCount = parameterCount
        }
    }

    override fun visitParameterAnnotation(parameter: Int, descriptor: String, visible: Boolean): AnnotationVisitor? {
        val annotation = nodeFactory.Annotation(desc = descriptor, values = mutableListOf())
        if (visible) {
            while (visibleParameterAnnotations.size <= parameter) {
                visibleParameterAnnotations.add(mutableListOf())
            }
            visibleParameterAnnotations[parameter].add(annotation)
        } else {
            while (invisibleParameterAnnotations.size <= parameter) {
                invisibleParameterAnnotations.add(mutableListOf())
            }
            invisibleParameterAnnotations[parameter].add(annotation)
        }
        return annotation
    }

    override fun visitAttribute(attribute: Attribute) {
        attrs.add(attribute)
    }

    override fun visitCode() {
        // Nothing to do.
    }

    override fun visitFrame(type: Int, numLocal: Int, local: List<Any>?, numStack: Int, stack: List<Any>?) {
        when (type) {
            Opcodes.F_NEW -> instructions.F_NEW(
                getLabelNodes(local!!),
                getLabelNodes(stack!!)
            )
            Opcodes.F_FULL -> instructions.F_FULL(
                getLabelNodes(local!!),
                getLabelNodes(stack!!)
            )
            Opcodes.F_APPEND -> instructions.F_APPEND(
                getLabelNodes(local!!)
            )
            Opcodes.F_CHOP -> instructions.F_CHOP(getLabelNodes(local!!))
            Opcodes.F_SAME -> instructions.F_SAME()
            Opcodes.F_SAME1 -> instructions.F_SAME1(getLabelNodes(stack!!))
            else -> {
                throw IllegalArgumentException("Invalid frame type: $type")
            }
        }
    }

    override fun visitInsn(opcode: Int) {
        when (opcode) {
            Opcodes.NOP -> instructions.NOP()
            Opcodes.ACONST_NULL -> instructions.ACONST_NULL()
            Opcodes.ICONST_M1 -> instructions.ICONST_M1()
            Opcodes.ICONST_0 -> instructions.ICONST_0()
            Opcodes.ICONST_1 -> instructions.ICONST_1()
            Opcodes.ICONST_2 -> instructions.ICONST_2()
            Opcodes.ICONST_3 -> instructions.ICONST_3()
            Opcodes.ICONST_4 -> instructions.ICONST_4()
            Opcodes.ICONST_5 -> instructions.ICONST_5()
            Opcodes.LCONST_0 -> instructions.LCONST_0()
            Opcodes.LCONST_1 -> instructions.LCONST_1()
            Opcodes.FCONST_0 -> instructions.FCONST_0()
            Opcodes.FCONST_1 -> instructions.FCONST_1()
            Opcodes.FCONST_2 -> instructions.FCONST_2()
            Opcodes.DCONST_0 -> instructions.DCONST_0()
            Opcodes.DCONST_1 -> instructions.DCONST_1()

            Opcodes.IALOAD -> instructions.IALOAD()
            Opcodes.LALOAD -> instructions.LALOAD()
            Opcodes.FALOAD -> instructions.FALOAD()
            Opcodes.DALOAD -> instructions.DALOAD()
            Opcodes.AALOAD -> instructions.AALOAD()
            Opcodes.BALOAD -> instructions.BALOAD()
            Opcodes.CALOAD -> instructions.CALOAD()
            Opcodes.SALOAD -> instructions.SALOAD()

            Opcodes.IASTORE -> instructions.IASTORE()
            Opcodes.LASTORE -> instructions.LASTORE()
            Opcodes.FASTORE -> instructions.FASTORE()
            Opcodes.DASTORE -> instructions.DASTORE()
            Opcodes.AASTORE -> instructions.AASTORE()
            Opcodes.BASTORE -> instructions.BASTORE()
            Opcodes.CASTORE -> instructions.CASTORE()
            Opcodes.SASTORE -> instructions.SASTORE()

            Opcodes.POP -> instructions.POP()
            Opcodes.POP2 -> instructions.POP2()
            Opcodes.DUP -> instructions.DUP()
            Opcodes.DUP_X1 -> instructions.DUP_X1()
            Opcodes.DUP_X2 -> instructions.DUP_X2()
            Opcodes.DUP2 -> instructions.DUP2()
            Opcodes.DUP2_X1 -> instructions.DUP2_X1()
            Opcodes.DUP2_X2 -> instructions.DUP2_X2()
            Opcodes.SWAP -> instructions.SWAP()

            Opcodes.IADD -> instructions.IADD()
            Opcodes.LADD -> instructions.LADD()
            Opcodes.FADD -> instructions.FADD()
            Opcodes.DADD -> instructions.DADD()
            Opcodes.ISUB -> instructions.ISUB()
            Opcodes.LSUB -> instructions.LSUB()
            Opcodes.FSUB -> instructions.FSUB()
            Opcodes.DSUB -> instructions.DSUB()
            Opcodes.IMUL -> instructions.IMUL()
            Opcodes.LMUL -> instructions.LMUL()
            Opcodes.FMUL -> instructions.FMUL()
            Opcodes.DMUL -> instructions.DMUL()
            Opcodes.IDIV -> instructions.IDIV()
            Opcodes.LDIV -> instructions.LDIV()
            Opcodes.FDIV -> instructions.FDIV()
            Opcodes.DDIV -> instructions.DDIV()
            Opcodes.IREM -> instructions.IREM()
            Opcodes.LREM -> instructions.LREM()
            Opcodes.FREM -> instructions.FREM()
            Opcodes.DREM -> instructions.DREM()

            Opcodes.INEG -> instructions.INEG()
            Opcodes.LNEG -> instructions.LNEG()
            Opcodes.FNEG -> instructions.FNEG()
            Opcodes.DNEG -> instructions.DNEG()

            Opcodes.ISHL -> instructions.ISHL()
            Opcodes.LSHL -> instructions.LSHL()
            Opcodes.ISHR -> instructions.ISHR()
            Opcodes.LSHR -> instructions.LSHR()
            Opcodes.IUSHR -> instructions.IUSHR()
            Opcodes.LUSHR -> instructions.LUSHR()

            Opcodes.IAND -> instructions.IAND()
            Opcodes.LAND -> instructions.LAND()
            Opcodes.IOR -> instructions.IOR()
            Opcodes.LOR -> instructions.LOR()
            Opcodes.IXOR -> instructions.IXOR()
            Opcodes.LXOR -> instructions.LXOR()

            Opcodes.I2L -> instructions.I2L()
            Opcodes.I2F -> instructions.I2F()
            Opcodes.I2D -> instructions.I2D()
            Opcodes.L2I -> instructions.L2I()
            Opcodes.L2F -> instructions.L2F()
            Opcodes.L2D -> instructions.L2D()
            Opcodes.F2I -> instructions.F2I()
            Opcodes.F2L -> instructions.F2L()
            Opcodes.F2D -> instructions.F2D()
            Opcodes.D2I -> instructions.D2I()
            Opcodes.D2L -> instructions.D2L()
            Opcodes.D2F -> instructions.D2F()

            Opcodes.I2B -> instructions.I2B()
            Opcodes.I2C -> instructions.I2C()
            Opcodes.I2S -> instructions.I2S()

            Opcodes.LCMP -> instructions.LCMP()
            Opcodes.FCMPL -> instructions.FCMPL()
            Opcodes.FCMPG -> instructions.FCMPG()
            Opcodes.DCMPL -> instructions.DCMPL()
            Opcodes.DCMPG -> instructions.DCMPG()

            Opcodes.IRETURN -> instructions.IRETURN()
            Opcodes.LRETURN -> instructions.LRETURN()
            Opcodes.FRETURN -> instructions.FRETURN()
            Opcodes.DRETURN -> instructions.DRETURN()
            Opcodes.ARETURN -> instructions.ARETURN()
            Opcodes.RETURN -> instructions.RETURN()

            Opcodes.ARRAYLENGTH -> instructions.ARRAYLENGTH()
            Opcodes.ATHROW -> instructions.ATHROW()
            Opcodes.MONITORENTER -> instructions.MONITORENTER()
            Opcodes.MONITOREXIT -> instructions.MONITOREXIT()

            else -> throw IllegalArgumentException("Invalid opcode for visitInsn: $opcode")
        }
    }

    override fun visitIntInsn(opcode: Int, operand: Int) {
        when (opcode) {
            Opcodes.BIPUSH -> instructions.BIPUSH(operand.toByte())
            Opcodes.SIPUSH -> instructions.SIPUSH(operand.toShort())
            Opcodes.NEWARRAY -> instructions.NEWARRAY(NewArrayInsnNode.NewArrayType.fromValue(operand))
            else -> throw IllegalArgumentException("Invalid opcode for visitIntInsn: $opcode")
        }
    }

    override fun visitVarInsn(opcode: Int, varIndex: Int) {
        when (opcode) {
            Opcodes.ILOAD -> instructions.ILOAD(varIndex)
            Opcodes.LLOAD -> instructions.LLOAD(varIndex)
            Opcodes.FLOAD -> instructions.FLOAD(varIndex)
            Opcodes.DLOAD -> instructions.DLOAD(varIndex)
            Opcodes.ALOAD -> instructions.ALOAD(varIndex)

            Opcodes.ISTORE -> instructions.ISTORE(varIndex)
            Opcodes.LSTORE -> instructions.LSTORE(varIndex)
            Opcodes.FSTORE -> instructions.FSTORE(varIndex)
            Opcodes.DSTORE -> instructions.DSTORE(varIndex)
            Opcodes.ASTORE -> instructions.ASTORE(varIndex)

            else -> throw IllegalArgumentException("Invalid opcode for visitVarInsn: $opcode")
        }
    }

    override fun visitTypeInsn(opcode: Int, type: String) {
        when (opcode) {
            Opcodes.NEW -> instructions.NEW(type)
            Opcodes.ANEWARRAY -> instructions.ANEWARRAY(type)
            Opcodes.CHECKCAST -> instructions.CHECKCAST(type)
            Opcodes.INSTANCEOF -> instructions.INSTANCEOF(type)
            else -> throw IllegalArgumentException("Invalid opcode for visitTypeInsn: $opcode")
        }
    }

    override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
        when (opcode) {
            Opcodes.GETSTATIC -> instructions.GETSTATIC(owner, name, descriptor)
            Opcodes.PUTSTATIC -> instructions.PUTSTATIC(owner, name, descriptor)
            Opcodes.GETFIELD -> instructions.GETFIELD(owner, name, descriptor)
            Opcodes.PUTFIELD -> instructions.PUTFIELD(owner, name, descriptor)
            else -> throw IllegalArgumentException("Invalid opcode for visitFieldInsn: $opcode")
        }
    }

    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
        when (opcode) {
            Opcodes.INVOKEVIRTUAL -> instructions.INVOKEVIRTUAL(owner, name, descriptor)
            Opcodes.INVOKESPECIAL -> instructions.INVOKESPECIAL(owner, name, descriptor)
            Opcodes.INVOKESTATIC -> instructions.INVOKESTATIC(owner, name, descriptor)
            Opcodes.INVOKEINTERFACE -> instructions.INVOKEINTERFACE(owner, name, descriptor)
            else -> throw IllegalArgumentException("Invalid opcode for visitMethodInsn: $opcode")
        }
    }

    override fun visitInvokeDynamicInsn(
        name: String,
        descriptor: String,
        bootstrapMethodHandle: Handle,
        bootstrapMethodArguments: List<Any>
    ) {
        instructions.INVOKEDYNAMIC(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments)
    }

    override fun visitJumpInsn(opcode: Int, label: Label) {
        val labelNode = getLabelNode(label)
        when (opcode) {
            Opcodes.IFEQ -> instructions.IFEQ(labelNode)
            Opcodes.IFNE -> instructions.IFNE(labelNode)
            Opcodes.IFLT -> instructions.IFLT(labelNode)
            Opcodes.IFGE -> instructions.IFGE(labelNode)
            Opcodes.IFGT -> instructions.IFGT(labelNode)
            Opcodes.IFLE -> instructions.IFLE(labelNode)
            Opcodes.IF_ICMPEQ -> instructions.IF_ICMPEQ(labelNode)
            Opcodes.IF_ICMPNE -> instructions.IF_ICMPNE(labelNode)
            Opcodes.IF_ICMPLT -> instructions.IF_ICMPLT(labelNode)
            Opcodes.IF_ICMPGE -> instructions.IF_ICMPGE(labelNode)
            Opcodes.IF_ICMPGT -> instructions.IF_ICMPGT(labelNode)
            Opcodes.IF_ICMPLE -> instructions.IF_ICMPLE(labelNode)
            Opcodes.IF_ACMPEQ -> instructions.IF_ACMPEQ(labelNode)
            Opcodes.IF_ACMPNE -> instructions.IF_ACMPNE(labelNode)
            Opcodes.GOTO -> instructions.GOTO(labelNode)
            Opcodes.IFNULL -> instructions.IFNULL(labelNode)
            Opcodes.IFNONNULL -> instructions.IFNONNULL(labelNode)
            else -> throw IllegalArgumentException("Invalid opcode for visitJumpInsn: $opcode")
        }
    }

    override fun visitLabel(label: Label) {
        instructions.LABEL(getLabelNode(label))
    }

    override fun visitLdcInsn(value: Any) {
        instructions.LDC(value)
    }

    override fun visitIincInsn(varIndex: Int, increment: Int) {
        instructions.IINC(varIndex, increment)
    }

    override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, labels: List<Label>) {
        instructions.TABLESWITCH(
            min,
            max,
            getLabelNode(dflt),
            getLabelNodes(labels)
        )
    }

    override fun visitLookupSwitchInsn(dflt: Label, keys: List<Int>, labels: List<Label>) {
        instructions.LOOKUPSWITCH(
            getLabelNode(dflt),
            keys,
            getLabelNodes(labels)
        )
    }

    override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
        instructions.MULTIANEWARRAY(descriptor, numDimensions)
    }

    override fun visitInsnAnnotation(
        typeRef: Int,
        typePath: TypePath?,
        descriptor: String,
        visible: Boolean
    ): AnnotationVisitor {
        // Find the last real instruction, i.e. the instruction targeted by this annotation.
        var currentInsnIndex = instructions.lastIndex
        while (instructions[currentInsnIndex].opcode == -1) {
            currentInsnIndex--
        }

        // Add the annotation to this instruction.
        val typeAnnotation = nodeFactory.TypeAnnotationNode(
            typeRef = typeRef,
            typePath = typePath,
            desc = descriptor
        )

        instructions.addTypeAnnotation(currentInsnIndex, typeAnnotation, visible)

        return typeAnnotation
    }

    override fun visitTryCatchBlock(start: Label, end: Label, handler: Label?, type: String?) {
        val tryCatchBlock = nodeFactory.TryCatchBlockNode(
            getLabelNode(start),
            getLabelNode(end),
            getLabelNode(handler!!),
            type
        )
        tryCatchBlock.updateIndex(tryCatchBlocks.size)
        tryCatchBlocks.add(tryCatchBlock)
    }

    override fun visitTryCatchAnnotation(
        typeRef: Int,
        typePath: TypePath?,
        descriptor: String,
        visible: Boolean
    ): AnnotationVisitor? {
        val tryCatchBlock = tryCatchBlocks[(typeRef and 0x00FFFF00) shr 8]
        val typeAnnotation = nodeFactory.TypeAnnotationNode(
            typeRef = typeRef,
            typePath = typePath,
            desc = descriptor
        )
        if (visible) {
            tryCatchBlock.visibleTypeAnnotations.add(typeAnnotation)
        } else {
            tryCatchBlock.invisibleTypeAnnotations.add(typeAnnotation)
        }
        return typeAnnotation
    }

    override fun visitLocalVariable(
        name: String,
        descriptor: String,
        signature: String?,
        start: Label,
        end: Label,
        index: Int
    ) {
        val localVariable = nodeFactory.LocalVariableNode(
            name,
            descriptor,
            signature,
            getLabelNode(start),
            getLabelNode(end),
            index
        )
        localVariables.add(localVariable)
    }

    override fun visitLocalVariableAnnotation(
        typeRef: Int,
        typePath: TypePath?,
        start: List<Label>,
        end: List<Label>,
        index: List<Int>,
        descriptor: String,
        visible: Boolean
    ): AnnotationVisitor {
        val localVariableAnnotation = nodeFactory.LocalVariableAnnotationNode(
            typeRef,
            typePath,
            getLabelNodes(start),
            getLabelNodes(end),
            index,
            descriptor,
            visible
        )
        if (visible) {
            visibleLocalVariableAnnotations.add(localVariableAnnotation)
        } else {
            invisibleLocalVariableAnnotations.add(localVariableAnnotation)
        }
        return localVariableAnnotation
    }

    override fun visitLineNumber(line: Int, start: Label) {
        instructions.LINE(line, getLabelNode(start))
    }

    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        this.maxStack = maxStack
        this.maxLocals = maxLocals
    }

    override fun visitEnd() {
        // Nothing to do.
    }
}

private fun MutableMethodNode.getLabelNodes(objects: List<Label>): List<LabelNode> =
    objects.map {
        getLabelNode(it)
    }

private fun MutableMethodNode.getLabelNodes(objects: List<Any>): List<Any> = objects.map {
    if (it is Label) {
        getLabelNode(it)
    } else {
        it
    }
}