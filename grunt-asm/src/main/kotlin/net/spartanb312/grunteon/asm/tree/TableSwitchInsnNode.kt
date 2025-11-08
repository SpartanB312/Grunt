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

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * A node that represents a TABLESWITCH instruction.
 *
 * @author Eric Bruneton
 */
class TableSwitchInsnNode(
    /** The minimum key value.  */
    var min: Int,
    /** The maximum key value.  */
    var max: Int,
    /** Beginning of the default handler block.  */
    var dflt: LabelNode, vararg labels: LabelNode?
) : AbstractInsnNode(Opcodes.TABLESWITCH) {
    /** Beginnings of the handler blocks. This list is a list of [LabelNode] objects.  */
    var labels: MutableList<LabelNode?>

    /**
     * Constructs a new [TableSwitchInsnNode].
     *
     * @param min the minimum key value.
     * @param max the maximum key value.
     * @param dflt beginning of the default handler block.
     * @param labels beginnings of the handler blocks. `labels[i]` is the beginning of the
     * handler block for the `min + i` key.
     */
    init {
        this.labels = Util.asArrayList<LabelNode?>(labels)
    }

    override fun getType(): Int {
        return AbstractInsnNode.Companion.TABLESWITCH_INSN
    }

    override fun accept(methodVisitor: MethodVisitor) {
        val labelsArray = arrayOfNulls<Label>(this.labels.size)
        var i = 0
        val n = labelsArray.size
        while (i < n) {
            labelsArray[i] = this.labels.get(i)!!.getLabel()
            ++i
        }
        methodVisitor.visitTableSwitchInsn(min, max, dflt.getLabel(), *labelsArray)
        acceptAnnotations(methodVisitor)
    }

    override fun clone(clonedLabels: MutableMap<LabelNode?, LabelNode?>): AbstractInsnNode {
        return TableSwitchInsnNode(
            min,
            max,
            AbstractInsnNode.Companion.clone(dflt, clonedLabels),
            *AbstractInsnNode.Companion.clone(labels, clonedLabels)
        )
            .cloneAnnotations(this)
    }
}
