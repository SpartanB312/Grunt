@file:Suppress("FunctionName", "SpellCheckingInspection")

package net.spartanb312.grunteon.asm.tree

import net.spartanb312.grunteon.asm.MethodVisitor
import net.spartanb312.grunteon.asm.tree.insn.*
import org.objectweb.asm.Handle

interface InsnList : List<IBaseInsnNode> {
    fun accept(mv: MethodVisitor) {
        forEach { it.accept(mv) }
    }
}

interface InsnListBuilder {
    fun GETSTATIC(
        owner: String,
        name: String,
        desc: String
    )

    fun PUTSTATIC(
        owner: String,
        name: String,
        desc: String
    )

    fun GETFIELD(
        owner: String,
        name: String,
        desc: String
    )

    fun PUTFIELD(
        owner: String,
        name: String,
        desc: String
    )

    fun F_APPEND(
        local: List<Any>
    )

    fun F_FULL(
        local: List<Any>,
        stack: List<Any>
    )

    fun F_CHOP(
        local: List<Any>
    )

    fun F_SAME()

    fun F_SAME1(
        stack: List<Any>
    )

    fun IINC(
        variable: Int,
        increment: Int
    )

    fun INVOKEVIRTUAL(
        owner: String,
        name: String,
        desc: String
    )

    fun INVOKESPECIAL(
        owner: String,
        name: String,
        desc: String
    )

    fun INVOKESTATIC(
        owner: String,
        name: String,
        desc: String
    )

    fun INVOKEINTERFACE(
        owner: String,
        name: String,
        desc: String
    )

    fun INVOKEDYNAMIC(
        name: String,
        desc: String,
        bsm: Handle,
        bsmArgs: List<Any>
    )

    fun NOP()
    fun ACONST_NULL()
    fun ICONST_M1()
    fun ICONST_0()
    fun ICONST_1()
    fun ICONST_2()
    fun ICONST_3()
    fun ICONST_4()
    fun ICONST_5()
    fun LCONST_0()
    fun LCONST_1()
    fun FCONST_0()
    fun FCONST_1()
    fun FCONST_2()
    fun DCONST_0()
    fun DCONST_1()

    fun IALOAD()
    fun LALOAD()
    fun FALOAD()
    fun DALOAD()
    fun AALOAD()
    fun BALOAD()
    fun CALOAD()
    fun SALOAD()

    fun IASTORE()
    fun LASTORE()
    fun FASTORE()
    fun DASTORE()
    fun AASTORE()
    fun BASTORE()
    fun CASTORE()
    fun SASTORE()

    fun POP()
    fun POP2()
    fun DUP()
    fun DUP_X1()
    fun DUP_X2()
    fun DUP2()
    fun DUP2_X1()
    fun DUP2_X2()
    fun SWAP()

    fun IADD()
    fun LADD()
    fun FADD()
    fun DADD()
    fun ISUB()
    fun LSUB()
    fun FSUB()
    fun DSUB()
    fun IMUL()
    fun LMUL()
    fun FMUL()
    fun DMUL()
    fun IDIV()
    fun LDIV()
    fun FDIV()
    fun DDIV()
    fun IREM()
    fun LREM()
    fun FREM()
    fun DREM()

    fun INEG()
    fun LNEG()
    fun FNEG()
    fun DNEG()

    fun ISHL()
    fun LSHL()
    fun ISHR()
    fun LSHR()
    fun IUSHR()
    fun LUSHR()

    fun IAND()
    fun LAND()
    fun IOR()
    fun LOR()
    fun IXOR()
    fun LXOR()

    fun I2L()
    fun I2F()
    fun I2D()
    fun L2I()
    fun L2F()
    fun L2D()
    fun F2I()
    fun F2L()
    fun F2D()
    fun D2I()
    fun D2L()
    fun D2F()

    fun I2B()
    fun I2C()
    fun I2S()

    fun LCMP()
    fun FCMPL()
    fun FCMPG()
    fun DCMPL()
    fun DCMPG()

    fun IRETURN()
    fun LRETURN()
    fun FRETURN()
    fun DRETURN()
    fun ARETURN()
    fun RETURN()

    fun ARRAYLENGTH()
    fun ATHROW()
    fun MONITORENTER()
    fun MONITOREXIT()

    fun BIPUSH(byte: Byte)
    fun SIPUSH(short: Short)
    fun NEWARRAY(arrayType: NewArrayInsnNode.NewArrayType)

    fun build(): InsnList
}

fun InsnListBuilder.GETSTATIC(
    src: GetStaticInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = GETSTATIC(
    owner,
    name,
    desc
)

fun InsnListBuilder.PUTSTATIC(
    src: GetStaticInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = PUTSTATIC(
    owner,
    name,
    desc
)

fun InsnListBuilder.GETFIELD(
    src: GetStaticInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = GETFIELD(
    owner,
    name,
    desc
)

fun InsnListBuilder.PUTFIELD(
    src: GetStaticInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = PUTFIELD(
    owner,
    name,
    desc
)

fun InsnListBuilder.F_APPEND(
    src: FAppendNode,
    local: List<Any> = src.local
) = F_APPEND(
    local
)

fun InsnListBuilder.F_FULL(
    src: FFullNode,
    local: List<Any> = src.local,
    stack: List<Any> = src.stack
) = F_FULL(
    local,
    stack
)

fun InsnListBuilder.F_CHOP(
    src: FAppendNode,
    local: List<Any> = src.local
) = F_CHOP(
    local
)

fun InsnListBuilder.F_SAME1(
    src: FSame1Node,
    stack: List<Any> = src.stack
) = F_SAME1(
    stack
)

fun InsnListBuilder.IINC(
    src: IincInsnNode,
    variable: Int = src.variable,
    increment: Int = src.increment
) = IINC(
    variable,
    increment
)

fun InsnListBuilder.INVOKEVIRTUAL(
    src: InvokeVirtualInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = INVOKEVIRTUAL(
    owner,
    name,
    desc
)

fun InsnListBuilder.INVOKESPECIAL(
    src: InvokeSpecialInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = INVOKESPECIAL(
    owner,
    name,
    desc
)

fun InsnListBuilder.INVOKESTATIC(
    src: InvokeStaticInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = INVOKESTATIC(
    owner,
    name,
    desc
)

fun InsnListBuilder.INVOKEINTERFACE(
    src: InvokeInterfaceInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = INVOKEINTERFACE(
    owner,
    name,
    desc
)

fun InsnListBuilder.INVOKEDYNAMIC(
    src: InvokeDynamicInsnNode,
    name: String = src.name,
    desc: String = src.desc,
    bsm: Handle = src.bsm,
    bsmArgs: List<Any> = src.bsmArgs
) = INVOKEDYNAMIC(
    name,
    desc,
    bsm,
    bsmArgs
)

fun InsnListBuilder.BIPUSH(
    src: BiPushInsnNode,
    byte: Byte = src.operand.toByte()
) = BIPUSH(byte)

fun InsnListBuilder.SIPUSH(
    src: SiPushInsnNode,
    short: Short = src.operand.toShort()
) = SIPUSH(short)

fun InsnListBuilder.NEWARRAY(
    src: NewArrayInsnNode,
    arrayType: NewArrayInsnNode.NewArrayType = NewArrayInsnNode.NewArrayType.fromValue(src.operand)
) = NEWARRAY(arrayType)