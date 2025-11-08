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

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor

/** An [AbstractInsnNode] that encapsulates a [Label].  */
class LabelNode : AbstractInsnNode {
    private var value: Label? = null

    constructor() : super(-1)

    constructor(label: Label?) : super(-1) {
        this.value = label
    }

    override fun getType(): Int {
        return LABEL
    }

    val label: Label
        /**
         * Returns the label encapsulated by this node. A new label is created and associated with this
         * node if it was created without an encapsulated label.
         *
         * @return the label encapsulated by this node.
         */
        get() {
            if (value == null) {
                value = Label()
            }
            return value
        }

    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitLabel(this.label)
    }

    override fun clone(clonedLabels: MutableMap<LabelNode?, LabelNode?>): AbstractInsnNode? {
        return clonedLabels.get(this)
    }

    fun resetLabel() {
        value = null
    }
}
