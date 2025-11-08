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

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

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
 */
class FrameNode : AbstractInsnNode {
    /**
     * The type of this frame. Must be [Opcodes.F_NEW] for expanded frames, or [ ][Opcodes.F_FULL], [Opcodes.F_APPEND], [Opcodes.F_CHOP], [Opcodes.F_SAME] or
     * [Opcodes.F_APPEND], [Opcodes.F_SAME1] for compressed frames.
     */
    var type: Int = 0

    /**
     * The types of the local variables of this stack map frame. Elements of this list can be Integer,
     * String or LabelNode objects (for primitive, reference and uninitialized types respectively -
     * see [MethodVisitor]).
     */
    var local: MutableList<Any?>? = null

    /**
     * The types of the operand stack elements of this stack map frame. Elements of this list can be
     * Integer, String or LabelNode objects (for primitive, reference and uninitialized types
     * respectively - see [MethodVisitor]).
     */
    var stack: MutableList<Any?>? = null

    private constructor() : super(-1)

    /**
     * Constructs a new [FrameNode].
     *
     * @param type the type of this frame. Must be [Opcodes.F_NEW] for expanded frames, or
     * [Opcodes.F_FULL], [Opcodes.F_APPEND], [Opcodes.F_CHOP], [     ][Opcodes.F_SAME] or [Opcodes.F_APPEND], [Opcodes.F_SAME1] for compressed frames.
     * @param numLocal number of local variables of this stack map frame. Long and double values count
     * for one variable.
     * @param local the types of the local variables of this stack map frame. Elements of this list
     * can be Integer, String or LabelNode objects (for primitive, reference and uninitialized
     * types respectively - see [MethodVisitor]). Long and double values are represented by
     * a single element.
     * @param numStack number of operand stack elements of this stack map frame. Long and double
     * values count for one stack element.
     * @param stack the types of the operand stack elements of this stack map frame. Elements of this
     * list can be Integer, String or LabelNode objects (for primitive, reference and
     * uninitialized types respectively - see [MethodVisitor]). Long and double values are
     * represented by a single element.
     */
    constructor(
        type: Int,
        numLocal: Int,
        local: Array<Any?>?,
        numStack: Int,
        stack: Array<Any?>?
    ) : super(-1) {
        this.type = type
        when (type) {
            Opcodes.F_NEW, Opcodes.F_FULL -> {
                this.local = Util.asArrayList<Any?>(numLocal, local)
                this.stack = Util.asArrayList<Any?>(numStack, stack)
            }
            Opcodes.F_APPEND -> this.local = Util.asArrayList<Any?>(numLocal, local)
            Opcodes.F_CHOP -> this.local = Util.asArrayList<Any?>(numLocal)
            Opcodes.F_SAME -> {}
            Opcodes.F_SAME1 -> this.stack = Util.asArrayList<Any?>(1, stack)
            else -> throw IllegalArgumentException()
        }
    }

    override fun getType(): Int {
        return AbstractInsnNode.Companion.FRAME
    }

    override fun accept(methodVisitor: MethodVisitor) {
        when (type) {
            Opcodes.F_NEW, Opcodes.F_FULL -> methodVisitor.visitFrame(
                type,
                local!!.size,
                Companion.asArray(local!!),
                stack!!.size,
                Companion.asArray(stack!!)
            )
            Opcodes.F_APPEND -> methodVisitor.visitFrame(type, local!!.size, Companion.asArray(local!!), 0, null)
            Opcodes.F_CHOP -> methodVisitor.visitFrame(type, local!!.size, null, 0, null)
            Opcodes.F_SAME -> methodVisitor.visitFrame(type, 0, null, 0, null)
            Opcodes.F_SAME1 -> methodVisitor.visitFrame(type, 0, null, 1, Companion.asArray(stack!!))
            else -> throw IllegalArgumentException()
        }
    }

    override fun clone(clonedLabels: MutableMap<LabelNode?, LabelNode?>): AbstractInsnNode {
        val clone = FrameNode()
        clone.type = type
        if (local != null) {
            clone.local = ArrayList<Any?>()
            var i = 0
            val n = local!!.size
            while (i < n) {
                var localElement = local!!.get(i)
                if (localElement is LabelNode) {
                    localElement = clonedLabels.get(localElement)
                }
                clone.local!!.add(localElement)
                ++i
            }
        }
        if (stack != null) {
            clone.stack = ArrayList<Any?>()
            var i = 0
            val n = stack!!.size
            while (i < n) {
                var stackElement = stack!!.get(i)
                if (stackElement is LabelNode) {
                    stackElement = clonedLabels.get(stackElement)
                }
                clone.stack!!.add(stackElement)
                ++i
            }
        }
        return clone
    }

    companion object {
        private fun asArray(list: MutableList<Any?>): Array<Any?> {
            val array = arrayOfNulls<Any>(list.size)
            var i = 0
            val n = array.size
            while (i < n) {
                var o = list.get(i)
                if (o is LabelNode) {
                    o = o.getLabel()
                }
                array[i] = o
                ++i
            }
            return array
        }
    }
}
