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
import org.objectweb.asm.MethodVisitor

/**
 * A node that represents a bytecode instruction. *An instruction can appear at most once in at
 * most one [InsnList] at a time*.
 *
 * @author Eric Bruneton
 */
abstract class AbstractInsnNode protected constructor(
    /**
     * The opcode of this instruction, or -1 if this is not a JVM instruction (e.g. a label or a line
     * number).
     */
    var opcode: Int
) {
    /**
     * Returns the opcode of this instruction.
     *
     * @return the opcode of this instruction, or -1 if this is not a JVM instruction (e.g. a label or
     * a line number).
     */

    /**
     * The runtime visible type annotations of this instruction. This field is only used for real
     * instructions (i.e. not for labels, frames, or line number nodes). This list is a list of [ ] objects. May be null.
     */
    var visibleTypeAnnotations: MutableList<TypeAnnotationNode>? = null

    /**
     * The runtime invisible type annotations of this instruction. This field is only used for real
     * instructions (i.e. not for labels, frames, or line number nodes). This list is a list of [ ] objects. May be null.
     */
    var invisibleTypeAnnotations: MutableList<TypeAnnotationNode>? = null

    /**
     * Returns the previous instruction in the list to which this instruction belongs, if any.
     *
     * @return the previous instruction in the list to which this instruction belongs, if any. May be
     * null.
     */
    /** The previous instruction in the list to which this instruction belongs.  */
    var previous: AbstractInsnNode? = null

    /**
     * Returns the next instruction in the list to which this instruction belongs, if any.
     *
     * @return the next instruction in the list to which this instruction belongs, if any. May be
     * null.
     */
    /** The next instruction in the list to which this instruction belongs.  */
    var next: AbstractInsnNode? = null

    /**
     * The index of this instruction in the list to which it belongs. The value of this field is
     * correct only when [InsnList.cache] is not null. A value of -1 indicates that this
     * instruction does not belong to any [InsnList].
     */
    var index: Int

    /**
     * Constructs a new [AbstractInsnNode].
     *
     * @param opcode the opcode of the instruction to be constructed.
     */
    init {
        this.index = -1
    }

    /**
     * Returns the type of this instruction.
     *
     * @return the type of this instruction, i.e. one the constants defined in this class.
     */
    abstract val type: Int

    /**
     * Makes the given method visitor visit this instruction.
     *
     * @param methodVisitor a method visitor.
     */
    abstract fun accept(methodVisitor: MethodVisitor?)

    /**
     * Makes the given visitor visit the annotations of this instruction.
     *
     * @param methodVisitor a method visitor.
     */
    protected fun acceptAnnotations(methodVisitor: MethodVisitor) {
        if (visibleTypeAnnotations != null) {
            var i = 0
            val n = visibleTypeAnnotations!!.size
            while (i < n) {
                val typeAnnotation: TypeAnnotationNode = visibleTypeAnnotations!!.get(i)
                typeAnnotation.accept(
                    methodVisitor.visitInsnAnnotation(
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
                    methodVisitor.visitInsnAnnotation(
                        typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, false
                    )
                )
                ++i
            }
        }
    }

    /**
     * Returns a copy of this instruction.
     *
     * @param clonedLabels a map from LabelNodes to cloned LabelNodes.
     * @return a copy of this instruction. The returned instruction does not belong to any [     ].
     */
    abstract fun clone(clonedLabels: MutableMap<LabelNode?, LabelNode?>?): AbstractInsnNode?

    /**
     * Clones the annotations of the given instruction into this instruction.
     *
     * @param insnNode the source instruction.
     * @return this instruction.
     */
    protected fun cloneAnnotations(insnNode: AbstractInsnNode): AbstractInsnNode {
        if (insnNode.visibleTypeAnnotations != null) {
            this.visibleTypeAnnotations = ArrayList<TypeAnnotationNode>()
            var i = 0
            val n = insnNode.visibleTypeAnnotations!!.size
            while (i < n) {
                val sourceAnnotation: TypeAnnotationNode = insnNode.visibleTypeAnnotations!!.get(i)
                val cloneAnnotation: TypeAnnotationNode =
                    TypeAnnotationNode(
                        sourceAnnotation.typeRef, sourceAnnotation.typePath, sourceAnnotation.desc
                    )
                sourceAnnotation.accept(cloneAnnotation)
                this.visibleTypeAnnotations!!.add(cloneAnnotation)
                ++i
            }
        }
        if (insnNode.invisibleTypeAnnotations != null) {
            this.invisibleTypeAnnotations = ArrayList<TypeAnnotationNode>()
            var i = 0
            val n = insnNode.invisibleTypeAnnotations!!.size
            while (i < n) {
                val sourceAnnotation: TypeAnnotationNode = insnNode.invisibleTypeAnnotations!!.get(i)
                val cloneAnnotation: TypeAnnotationNode =
                    TypeAnnotationNode(
                        sourceAnnotation.typeRef, sourceAnnotation.typePath, sourceAnnotation.desc
                    )
                sourceAnnotation.accept(cloneAnnotation)
                this.invisibleTypeAnnotations!!.add(cloneAnnotation)
                ++i
            }
        }
        return this
    }

    companion object {
        /** The type of [InsnNode] instructions.  */
        const val INSN: Int = 0

        /** The type of [IntInsnNode] instructions.  */
        const val INT_INSN: Int = 1

        /** The type of [VarInsnNode] instructions.  */
        const val VAR_INSN: Int = 2

        /** The type of [TypeInsnNode] instructions.  */
        const val TYPE_INSN: Int = 3

        /** The type of [FieldInsnNode] instructions.  */
        const val FIELD_INSN: Int = 4

        /** The type of [MethodInsnNode] instructions.  */
        const val METHOD_INSN: Int = 5

        /** The type of [InvokeDynamicInsnNode] instructions.  */
        const val INVOKE_DYNAMIC_INSN: Int = 6

        /** The type of [JumpInsnNode] instructions.  */
        const val JUMP_INSN: Int = 7

        /** The type of [LabelNode] "instructions".  */
        const val LABEL: Int = 8

        /** The type of [LdcInsnNode] instructions.  */
        const val LDC_INSN: Int = 9

        /** The type of [IincInsnNode] instructions.  */
        const val IINC_INSN: Int = 10

        /** The type of [TableSwitchInsnNode] instructions.  */
        const val TABLESWITCH_INSN: Int = 11

        /** The type of [LookupSwitchInsnNode] instructions.  */
        const val LOOKUPSWITCH_INSN: Int = 12

        /** The type of [MultiANewArrayInsnNode] instructions.  */
        const val MULTIANEWARRAY_INSN: Int = 13

        /** The type of [FrameNode] "instructions".  */
        const val FRAME: Int = 14

        /** The type of [LineNumberNode] "instructions".  */
        const val LINE: Int = 15

        /**
         * Returns the clone of the given label.
         *
         * @param label a label.
         * @param clonedLabels a map from LabelNodes to cloned LabelNodes.
         * @return the clone of the given label.
         */
        fun clone(label: LabelNode?, clonedLabels: MutableMap<LabelNode?, LabelNode?>): LabelNode? {
            return clonedLabels.get(label)
        }

        /**
         * Returns the clones of the given labels.
         *
         * @param labels a list of labels.
         * @param clonedLabels a map from LabelNodes to cloned LabelNodes.
         * @return the clones of the given labels.
         */
        fun clone(
            labels: MutableList<LabelNode?>, clonedLabels: MutableMap<LabelNode?, LabelNode?>
        ): Array<LabelNode?> {
            val clones = arrayOfNulls<LabelNode>(labels.size)
            var i = 0
            val n = clones.size
            while (i < n) {
                clones[i] = clonedLabels.get(labels.get(i))
                ++i
            }
            return clones
        }
    }
}
