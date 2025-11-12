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
 * A node that represents a zero operand instruction.
 *
 * @author Eric Bruneton
 * @author Luna
 */
sealed interface IInsnNode : IBaseInsnNode {
    /**
     * The opcode of the instruction to be constructed. This opcode must be NOP,
     * ACONST_NULL, ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
     * LCONST_0, LCONST_1, FCONST_0, FCONST_1, FCONST_2, DCONST_0, DCONST_1, IALOAD, LALOAD,
     * FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD, IASTORE, LASTORE, FASTORE, DASTORE,
     * AASTORE, BASTORE, CASTORE, SASTORE, POP, POP2, DUP, DUP_X1, DUP_X2, DUP2, DUP2_X1, DUP2_X2,
     * SWAP, IADD, LADD, FADD, DADD, ISUB, LSUB, FSUB, DSUB, IMUL, LMUL, FMUL, DMUL, IDIV, LDIV,
     * FDIV, DDIV, IREM, LREM, FREM, DREM, INEG, LNEG, FNEG, DNEG, ISHL, LSHL, ISHR, LSHR, IUSHR,
     * LUSHR, IAND, LAND, IOR, LOR, IXOR, LXOR, I2L, I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D, D2I,
     * D2L, D2F, I2B, I2C, I2S, LCMP, FCMPL, FCMPG, DCMPL, DCMPG, IRETURN, LRETURN, FRETURN,
     * DRETURN, ARETURN, RETURN, ARRAYLENGTH, ATHROW, MONITORENTER, or MONITOREXIT.
     */
    override val opcode: Int

    override val type: Int
        get() = AbstractInsnNode.INSN

    override fun accept(methodVisitor: MethodVisitor) {
        methodVisitor.visitInsn(opcode)
        IBaseInsnNode.acceptAnnotations(this, methodVisitor)
    }
}

interface AConstNullInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.ACONST_NULL
}

interface IConstM1InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.ICONST_M1
}

interface IConst0InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.ICONST_0
}

interface IConst1InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.ICONST_1
}

interface IConst2InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.ICONST_2
}

interface IConst3InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.ICONST_3
}

interface IConst4InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.ICONST_4
}

interface IConst5InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.ICONST_5
}

interface LConst0InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.LCONST_0
}

interface LConst1InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.LCONST_1
}

interface FConst0InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.FCONST_0
}

interface FConst1InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.FCONST_1
}

interface FConst2InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.FCONST_2
}

interface DConst0InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DCONST_0
}

interface DConst1InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DCONST_1
}

interface IALoadInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.IALOAD
}

interface LALoadInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.LALOAD
}

interface FALoadInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.FALOAD
}

interface DALoadInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DALOAD
}

interface AALoadInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.AALOAD
}

interface BALoadInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.BALOAD
}

interface CALoadInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.CALOAD
}

interface SALoadInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.SALOAD
}

interface IAStoreInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.IASTORE
}

interface LAStoreInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.LASTORE
}

interface FAStoreInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.FASTORE
}

interface DAStoreInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DASTORE
}

interface AAStoreInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.AASTORE
}

interface BAStoreInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.BASTORE
}

interface CAStoreInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.CASTORE
}

interface SAStoreInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.SASTORE
}

interface PopInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.POP
}

interface Pop2InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.POP2
}

interface DupInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DUP
}

interface DupX1InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DUP_X1
}

interface DupX2InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DUP_X2
}

interface Dup2InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DUP2
}

interface Dup2X1InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DUP2_X1
}

interface Dup2X2InsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DUP2_X2
}

interface SwapInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.SWAP
}

interface IAddInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.IADD
}

interface LAddInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.LADD
}

interface FAddInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.FADD
}

interface DAddInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DADD
}

interface ISubInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.ISUB
}

interface LSubInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.LSUB
}

interface FSubInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.FSUB
}

interface DSubInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DSUB
}

interface IMulInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.IMUL
}

interface LMulInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.LMUL
}

interface FMulInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.FMUL
}

interface DMulInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DMUL
}

interface IDivInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.IDIV
}

interface LDivInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.LDIV
}

interface FDivInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.FDIV
}

interface DDivInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DDIV
}

interface IRemInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.IREM
}

interface LRemInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.LREM
}

interface FRemInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.FREM
}

interface DRemInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DREM
}

interface INegInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.INEG
}

interface LNegInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.LNEG
}

interface FNegInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.FNEG
}

interface DNegInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DNEG
}

interface IShlInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.ISHL
}

interface LShlInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.LSHL
}

interface IShrInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.ISHR
}

interface LShrInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.LSHR
}

interface IUshrInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.IUSHR
}

interface LUshrInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.LUSHR
}

interface IAndInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.IAND
}

interface LAndInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.LAND
}

interface IOrInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.IOR
}

interface LOrInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.LOR
}

interface IXorInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.IXOR
}

interface LXorInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.LXOR
}

interface I2LInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.I2L
}

interface I2FInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.I2F
}

interface I2DInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.I2D
}

interface L2IInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.L2I
}

interface L2FInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.L2F
}

interface L2DInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.L2D
}

interface F2IInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.F2I
}

interface F2LInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.F2L
}

interface F2DInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.F2D
}

interface D2IInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.D2I
}

interface D2LInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.D2L
}

interface D2FInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.D2F
}

interface I2BInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.I2B
}

interface I2CInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.I2C
}

interface I2SInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.I2S
}

interface LCmpInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.LCMP
}

interface FCmplInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.FCMPL
}

interface FCmpgInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.FCMPG
}

interface DCmplInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DCMPL
}

interface DCmpgInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DCMPG
}

interface IReturnInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.IRETURN
}

interface LReturnInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.LRETURN
}

interface FReturnInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.FRETURN
}

interface DReturnInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.DRETURN
}

interface AReturnInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.ARETURN
}

interface ReturnInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.RETURN
}

interface ArrayLengthInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.ARRAYLENGTH
}

interface AThrowInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.ATHROW
}

interface MonitorEnterInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.MONITORENTER
}

interface MonitorExitInsnNode : IInsnNode {
    override val opcode: Int
        get() = Opcodes.MONITOREXIT
}
