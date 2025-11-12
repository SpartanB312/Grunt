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
package net.spartanb312.grunteon.asm.tree.insn

import net.spartanb312.grunteon.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode

/**
 * A node that represents a stack map frame. These nodes are pseudo instruction nodes in order to be
 * inserted in an instruction list. In fact these nodes must(*) be inserted *just before* any
 * instruction node **i** that follows an unconditionnal branch instruction such as GOTO or
 * THROW, that is the target of a jump instruction, or that starts an exception handler block. The
 * stack map frame types must describe the values of the local variables and of the operand stack
 * elements *just before* **i** is executed. <br></br>
 * <br></br>
 * (*) this is mandatory only for classes whose version is greater than or equal to [ ][Opcodes.V1_6].
 *
 * @author Eric Bruneton
 * @author Luna
 */
sealed interface IFrameNode : IBaseInsnNode {
    override val opcode: Int
        get() = -1

    /**
     * The type of this frame. Must be [Opcodes.F_NEW] for expanded frames, or [ ][Opcodes.F_FULL], [Opcodes.F_APPEND], [Opcodes.F_CHOP], [Opcodes.F_SAME] or
     * [Opcodes.F_APPEND], [Opcodes.F_SAME1] for compressed frames.
     */
    val frameType: Int

    /**
     * The types of the local variables of this stack map frame. Elements of this list can be Integer,
     * String or LabelNode objects (for primitive, reference and uninitialized types respectively -
     * see [MethodVisitor]).
     */
    val local: List<Any>?

    /**
     * The types of the operand stack elements of this stack map frame. Elements of this
     * list can be Integer, String or LabelNode objects (for primitive, reference and
     * uninitialized types respectively - see [MethodVisitor]). Long and double values are
     * represented by a single element.
     */
    val stack: List<Any>?

    override val type: Int
        get() = AbstractInsnNode.FRAME

//    override fun accept(methodVisitor: MethodVisitor) {
//        when (frameType) {
//            Opcodes.F_NEW, Opcodes.F_FULL -> methodVisitor.visitFrame(
//                frameType,
//                local!!.size,
//                unwrapLabel(local),
//                stack!!.size,
//                unwrapLabel(stack)
//            )
//            Opcodes.F_APPEND -> methodVisitor.visitFrame(frameType, local!!.size, unwrapLabel(local), 0, null)
//            Opcodes.F_CHOP -> methodVisitor.visitFrame(frameType, local!!.size, null, 0, null)
//            Opcodes.F_SAME -> methodVisitor.visitFrame(frameType, 0, null, 0, null)
//            Opcodes.F_SAME1 -> methodVisitor.visitFrame(frameType, 0, null, 1, unwrapLabel(stack))
//            else -> throw IllegalArgumentException()
//        }
//    }
}

private fun unwrapLabel(list: List<Any>): List<Any> {
    return list.map { if (it is LabelNode) it.value else it }
}

interface FNewNode : IFrameNode {
    override val frameType: Int
        get() = Opcodes.F_NEW

    override val local: List<Any>

    override val stack: List<Any>

    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitFrame(
            frameType,
            local.size,
            unwrapLabel(local),
            stack.size,
            unwrapLabel(stack)
        )
    }
}

interface FFullNode : IFrameNode {
    override val frameType: Int
        get() = Opcodes.F_FULL

    override val local: List<Any>

    override val stack: List<Any>

    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitFrame(
            frameType,
            local.size,
            unwrapLabel(local),
            stack.size,
            unwrapLabel(stack)
        )
    }
}

interface FAppendNode : IFrameNode {
    override val frameType: Int
        get() = Opcodes.F_APPEND

    override val local: List<Any>

    @Deprecated("DO NOT USE", level = DeprecationLevel.HIDDEN)
    override val stack: List<Any>?
        get() = null

    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitFrame(frameType, local.size, unwrapLabel(local), 0, null)
    }
}

interface FChopNode : IFrameNode {
    override val frameType: Int
        get() = Opcodes.F_CHOP

    override val local: List<Any>

    @Deprecated("DO NOT USE", level = DeprecationLevel.HIDDEN)
    override val stack: List<Any>?
        get() = null

    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitFrame(frameType, local.size, local, 0, null)
    }
}

interface FSameNode : IFrameNode {
    override val frameType: Int
        get() = Opcodes.F_SAME

    @Deprecated("DO NOT USE", level = DeprecationLevel.HIDDEN)
    override val local: List<Any>?
        get() = null

    @Deprecated("DO NOT USE", level = DeprecationLevel.HIDDEN)
    override val stack: List<Any>?
        get() = null

    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitFrame(frameType, 0, null, 0, null)
    }
}

interface FSame1Node : IFrameNode {
    override val frameType: Int
        get() = Opcodes.F_SAME1

    @Deprecated("DO NOT USE", level = DeprecationLevel.HIDDEN)
    override val local: List<Any>?
        get() = null

    override val stack: List<Any>

    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitFrame(frameType, 0, null, 1, unwrapLabel(stack))
    }
}