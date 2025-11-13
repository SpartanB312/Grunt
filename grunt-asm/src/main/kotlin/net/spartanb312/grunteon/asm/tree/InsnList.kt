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

interface MutableInsnList : InsnList {
    fun addTypeAnnotation(index: Int, typeAnnotationNode: TypeAnnotationNode, isVisible: Boolean)

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

    fun F_NEW(
        local: List<Any>,
        stack: List<Any>
    )

    fun F_FULL(
        local: List<Any>,
        stack: List<Any>
    )

    fun F_APPEND(
        local: List<Any>
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

    fun IFEQ(label: LabelNode)
    fun IFNE(label: LabelNode)
    fun IFLT(label: LabelNode)
    fun IFGE(label: LabelNode)
    fun IFGT(label: LabelNode)
    fun IFLE(label: LabelNode)
    fun IF_ICMPEQ(label: LabelNode)
    fun IF_ICMPNE(label: LabelNode)
    fun IF_ICMPLT(label: LabelNode)
    fun IF_ICMPGE(label: LabelNode)
    fun IF_ICMPGT(label: LabelNode)
    fun IF_ICMPLE(label: LabelNode)
    fun IF_ACMPEQ(label: LabelNode)
    fun IF_ACMPNE(label: LabelNode)
    fun GOTO(label: LabelNode)
    fun IFNULL(label: LabelNode)
    fun IFNONNULL(label: LabelNode)

    fun createLabelNode(): LabelNode
    fun LABEL(labelNode: LabelNode)

    fun LDC(constant: Any)

    fun LINE(line: Int, label: LabelNode)

    fun LOOKUPSWITCH(
        dflt: LabelNode,
        keys: List<Int>,
        labels: List<LabelNode>
    )

    fun TABLESWITCH(
        min: Int,
        max: Int,
        dflt: LabelNode,
        labels: List<LabelNode>
    )

    fun MULTIANEWARRAY(
        type: String,
        dims: Int
    )

    fun NEW(
        type: String
    )

    fun ANEWARRAY(
        type: String
    )

    fun CHECKCAST(
        type: String
    )

    fun INSTANCEOF(
        type: String
    )

    fun ILOAD(variable: Int)
    fun LLOAD(variable: Int)
    fun FLOAD(variable: Int)
    fun DLOAD(variable: Int)
    fun ALOAD(variable: Int)
    fun ISTORE(variable: Int)
    fun LSTORE(variable: Int)
    fun FSTORE(variable: Int)
    fun DSTORE(variable: Int)
    fun ASTORE(variable: Int)

    fun build(): InsnList
}

fun MutableInsnList.GETSTATIC(
    src: GetStaticInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = GETSTATIC(
    owner,
    name,
    desc
)

fun MutableInsnList.PUTSTATIC(
    src: GetStaticInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = PUTSTATIC(
    owner,
    name,
    desc
)

fun MutableInsnList.GETFIELD(
    src: GetStaticInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = GETFIELD(
    owner,
    name,
    desc
)

fun MutableInsnList.PUTFIELD(
    src: GetStaticInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = PUTFIELD(
    owner,
    name,
    desc
)

fun MutableInsnList.F_NEW(
    src: FNewNode,
    local: List<Any> = src.local,
    stack: List<Any> = src.stack
) = F_NEW(
    local,
    stack
)

fun MutableInsnList.F_FULL(
    src: FFullNode,
    local: List<Any> = src.local,
    stack: List<Any> = src.stack
) = F_FULL(
    local,
    stack
)

fun MutableInsnList.F_APPEND(
    src: FAppendNode,
    local: List<Any> = src.local
) = F_APPEND(
    local
)

fun MutableInsnList.F_CHOP(
    src: FAppendNode,
    local: List<Any> = src.local
) = F_CHOP(
    local
)

fun MutableInsnList.F_SAME1(
    src: FSame1Node,
    stack: List<Any> = src.stack
) = F_SAME1(
    stack
)

fun MutableInsnList.IINC(
    src: IincInsnNode,
    variable: Int = src.variable,
    increment: Int = src.increment
) = IINC(
    variable,
    increment
)

fun MutableInsnList.INVOKEVIRTUAL(
    src: InvokeVirtualInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = INVOKEVIRTUAL(
    owner,
    name,
    desc
)

fun MutableInsnList.INVOKESPECIAL(
    src: InvokeSpecialInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = INVOKESPECIAL(
    owner,
    name,
    desc
)

fun MutableInsnList.INVOKESTATIC(
    src: InvokeStaticInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = INVOKESTATIC(
    owner,
    name,
    desc
)

fun MutableInsnList.INVOKEINTERFACE(
    src: InvokeInterfaceInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = INVOKEINTERFACE(
    owner,
    name,
    desc
)

fun MutableInsnList.INVOKEDYNAMIC(
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

fun MutableInsnList.BIPUSH(
    src: BiPushInsnNode,
    byte: Byte = src.operand.toByte()
) = BIPUSH(byte)

fun MutableInsnList.SIPUSH(
    src: SiPushInsnNode,
    short: Short = src.operand.toShort()
) = SIPUSH(short)

fun MutableInsnList.NEWARRAY(
    src: NewArrayInsnNode,
    arrayType: NewArrayInsnNode.NewArrayType = NewArrayInsnNode.NewArrayType.fromValue(src.operand)
) = NEWARRAY(arrayType)

fun MutableInsnList.IFEQ(
    src: IfEqInsnNode,
    label: LabelNode = src.label
) = IFEQ(label)

fun MutableInsnList.IFNE(
    src: IfNeInsnNode,
    label: LabelNode = src.label
) = IFNE(label)

fun MutableInsnList.IFLT(
    src: IfLtInsnNode,
    label: LabelNode = src.label
) = IFLT(label)

fun MutableInsnList.IFGE(
    src: IfGeInsnNode,
    label: LabelNode = src.label
) = IFGE(label)

fun MutableInsnList.IFGT(
    src: IfGtInsnNode,
    label: LabelNode = src.label
) = IFGT(label)

fun MutableInsnList.IFLE(
    src: IfLeInsnNode,
    label: LabelNode = src.label
) = IFLE(label)

fun MutableInsnList.IF_ICMPEQ(
    src: IfIcmpEqInsnNode,
    label: LabelNode = src.label
) = IF_ICMPEQ(label)

fun MutableInsnList.IF_ICMPNE(
    src: IfIcmpNeInsnNode,
    label: LabelNode = src.label
) = IF_ICMPNE(label)

fun MutableInsnList.IF_ICMPLT(
    src: IfIcmpLtInsnNode,
    label: LabelNode = src.label
) = IF_ICMPLT(label)

fun MutableInsnList.IF_ICMPGE(
    src: IfIcmpGeInsnNode,
    label: LabelNode = src.label
) = IF_ICMPGE(label)

fun MutableInsnList.IF_ICMPGT(
    src: IfIcmpGtInsnNode,
    label: LabelNode = src.label
) = IF_ICMPGT(label)

fun MutableInsnList.IF_ICMPLE(
    src: IfIcmpLeInsnNode,
    label: LabelNode = src.label
) = IF_ICMPLE(label)

fun MutableInsnList.IF_ACMPEQ(
    src: IfAcmpEqInsnNode,
    label: LabelNode = src.label
) = IF_ACMPEQ(label)

fun MutableInsnList.IF_ACMPNE(
    src: IfAcmpNeInsnNode,
    label: LabelNode = src.label
) = IF_ACMPNE(label)

fun MutableInsnList.GOTO(
    src: GotoInsnNode,
    label: LabelNode = src.label
) = GOTO(label)

fun MutableInsnList.IFNULL(
    src: IfNullInsnNode,
    label: LabelNode = src.label
) = IFNULL(label)

fun MutableInsnList.IFNONNULL(
    src: IfNonNullInsnNode,
    label: LabelNode = src.label
) = IFNONNULL(label)

fun MutableInsnList.LDC(
    src: LdcInsnNode,
    constant: Any = src.constant
) = LDC(constant)

fun MutableInsnList.LINE(
    src: LineNumberNode,
    line: Int = src.line,
    label: LabelNode = src.start
) = LINE(line, label)

fun MutableInsnList.LOOKUPSWITCH(
    src: LookupSwitchInsnNode,
    dflt: LabelNode = src.dflt,
    keys: List<Int> = src.keys,
    labels: List<LabelNode> = src.labels
) = LOOKUPSWITCH(
    dflt,
    keys,
    labels
)

fun MutableInsnList.TABLESWITCH(
    src: TableSwitchInsnNode,
    min: Int = src.min,
    max: Int = src.max,
    dflt: LabelNode = src.dflt,
    labels: List<LabelNode> = src.labels
) = TABLESWITCH(
    min,
    max,
    dflt,
    labels
)

fun MutableInsnList.MULTIANEWARRAY(
    src: MultiANewArrayInsnNode,
    type: String = src.desc,
    dims: Int = src.dims
) = MULTIANEWARRAY(
    type,
    dims
)

fun MutableInsnList.NEW(
    src: NewInsnNode,
    type: String = src.desc
) = NEW(
    type
)

fun MutableInsnList.ANEWARRAY(
    src: ANewArrayInsnNode,
    type: String = src.desc
) = ANEWARRAY(
    type
)

fun MutableInsnList.CHECKCAST(
    src: CheckCastInsnNode,
    type: String = src.desc
) = CHECKCAST(
    type
)

fun MutableInsnList.INSTANCEOF(
    src: InstanceOfInsnNode,
    type: String = src.desc
) = INSTANCEOF(
    type
)

fun MutableInsnList.ILOAD(
    src: ILoadInsnNode,
    variable: Int = src.variable
) = ILOAD(variable)

fun MutableInsnList.LLOAD(
    src: LLoadInsnNode,
    variable: Int = src.variable
) = LLOAD(variable)

fun MutableInsnList.FLOAD(
    src: FLoadInsnNode,
    variable: Int = src.variable
) = FLOAD(variable)

fun MutableInsnList.DLOAD(
    src: DLoadInsnNode,
    variable: Int = src.variable
) = DLOAD(variable)

fun MutableInsnList.ALOAD(
    src: ALoadInsnNode,
    variable: Int = src.variable
) = ALOAD(variable)

fun MutableInsnList.ISTORE(
    src: IStoreInsnNode,
    variable: Int = src.variable
) = ISTORE(variable)

fun MutableInsnList.LSTORE(
    src: LStoreInsnNode,
    variable: Int = src.variable
) = LSTORE(variable)

fun MutableInsnList.FSTORE(
    src: FStoreInsnNode,
    variable: Int = src.variable
) = FSTORE(variable)

fun MutableInsnList.DSTORE(
    src: DStoreInsnNode,
    variable: Int = src.variable
) = DSTORE(variable)

fun MutableInsnList.ASTORE(
    src: AStoreInsnNode,
    variable: Int = src.variable
) = ASTORE(variable)