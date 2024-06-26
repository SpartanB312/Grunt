package net.spartanb312.grunt.utils.builder

import net.spartanb312.grunt.utils.extensions.toInsnNode
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*

fun method(
    access: Int = ACC_PUBLIC,
    name: String,
    descriptor: String,
    signature: String? = null,
    exceptions: Array<String>? = null,
    builder: MethodBuilder.() -> Unit,
): MethodNode {
    val node = MethodNode(access, name, descriptor, signature, exceptions)
    node.visitCode()
    MethodBuilder(node).builder()
    node.visitEnd()
    return node
}

fun insnList(builder: InsnListBuilder.() -> Unit): InsnList {
    val list = InsnList()
    InsnListBuilder(list).builder()
    return list
}

@DslMarker
annotation class InsnBuilder

@DslMarker
annotation class MethodBuilderDSL

class MethodBuilder(val methodNode: MethodNode) {
    @MethodBuilderDSL
    operator fun InsnList.unaryPlus() = methodNode.instructions.add(this)

    @MethodBuilderDSL
    fun Maxs(maxStack: Int, maxLocals: Int) = methodNode.visitMaxs(maxStack, maxLocals)

    @MethodBuilderDSL
    fun InsnList(builder: InsnListBuilder.() -> Unit) = +insnList(builder)
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
class InsnListBuilder(val insnList: InsnList) {

    @InsnBuilder
    operator fun AbstractInsnNode.unaryPlus() = insnList.add(this)

    @InsnBuilder
    operator fun Int.unaryPlus() = +InsnNode(this)

    fun getLabelNode(label: Label): LabelNode {
        if (label.info !is LabelNode) {
            label.info = LabelNode()
        }
        return label.info as LabelNode
    }

    fun getLabelNodes(objects: Array<Any?>): Array<Any?> {
        val labelNodes = arrayOfNulls<Any>(objects.size)
        var i = 0
        val n = objects.size
        while (i < n) {
            var o = objects[i]
            if (o is Label) {
                o = getLabelNode((o as Label?)!!)
            }
            labelNodes[i] = o
            ++i
        }
        return labelNodes
    }

}

@InsnBuilder
infix fun InsnListBuilder.INT(value: Int) = +value.toInsnNode()

@InsnBuilder
infix fun InsnListBuilder.FLOAT(value: Float) = +value.toInsnNode()

@InsnBuilder
infix fun InsnListBuilder.LABEL(labelNode: LabelNode) = +labelNode

@InsnBuilder
infix fun InsnListBuilder.LABEL(label: Label) = +getLabelNode(label)

@InsnBuilder
infix fun InsnListBuilder.insn(opcode: Int): InsnNode {
    val node = InsnNode(opcode)
    insnList.add(node)
    return node
}

@InsnBuilder
val InsnListBuilder.RETURN get() = insn(Opcodes.RETURN)

@InsnBuilder
val InsnListBuilder.ARETURN get() = insn(Opcodes.ARETURN)

@InsnBuilder
val InsnListBuilder.IRETURN get() = insn(Opcodes.IRETURN)

@InsnBuilder
val InsnListBuilder.LRETURN get() = insn(Opcodes.LRETURN)

@InsnBuilder
val InsnListBuilder.FRETURN get() = insn(Opcodes.FRETURN)

@InsnBuilder
val InsnListBuilder.DRETURN get() = insn(Opcodes.DRETURN)

@InsnBuilder
val InsnListBuilder.ACONST_NULL get() = insn(Opcodes.ACONST_NULL)

@InsnBuilder
val InsnListBuilder.POP get() = insn(Opcodes.POP)

@InsnBuilder
val InsnListBuilder.POP2 get() = insn(Opcodes.POP2)

@InsnBuilder
val InsnListBuilder.ATHROW get() = insn(Opcodes.ATHROW)

@InsnBuilder
val InsnListBuilder.INEG get() = insn(Opcodes.INEG)

@InsnBuilder
val InsnListBuilder.ISUB get() = insn(Opcodes.ISUB)

@InsnBuilder
val InsnListBuilder.IADD get() = insn(Opcodes.IADD)

@InsnBuilder
val InsnListBuilder.IMUL get() = insn(Opcodes.IMUL)

@InsnBuilder
val InsnListBuilder.IDIV get() = insn(Opcodes.IDIV)

@InsnBuilder
val InsnListBuilder.IOR get() = insn(Opcodes.IOR)

@InsnBuilder
val InsnListBuilder.IAND get() = insn(Opcodes.IAND)

@InsnBuilder
val InsnListBuilder.IXOR get() = insn(Opcodes.IXOR)

@InsnBuilder
val InsnListBuilder.IREM get() = insn(Opcodes.IREM)

@InsnBuilder
val InsnListBuilder.LNEG get() = insn(Opcodes.LNEG)

@InsnBuilder
val InsnListBuilder.LSUB get() = insn(Opcodes.LSUB)

@InsnBuilder
val InsnListBuilder.LADD get() = insn(Opcodes.LADD)

@InsnBuilder
val InsnListBuilder.LMUL get() = insn(Opcodes.LMUL)

@InsnBuilder
val InsnListBuilder.LOR get() = insn(Opcodes.LOR)

@InsnBuilder
val InsnListBuilder.LAND get() = insn(Opcodes.LAND)

@InsnBuilder
val InsnListBuilder.LXOR get() = insn(Opcodes.LXOR)

@InsnBuilder
val InsnListBuilder.LCMP get() = insn(Opcodes.LCMP)

@InsnBuilder
val InsnListBuilder.SWAP get() = insn(Opcodes.SWAP)

@InsnBuilder
val InsnListBuilder.DUP get() = insn(Opcodes.DUP)

@InsnBuilder
val InsnListBuilder.DUP_X1 get() = insn(Opcodes.DUP_X1)

@InsnBuilder
val InsnListBuilder.DUP_X2 get() = insn(Opcodes.DUP_X2)

@InsnBuilder
val InsnListBuilder.DUP2 get() = insn(Opcodes.DUP2)

@InsnBuilder
val InsnListBuilder.DUP2_X1 get() = insn(Opcodes.DUP2_X1)

@InsnBuilder
val InsnListBuilder.DUP2_X2 get() = insn(Opcodes.DUP2_X2)

@InsnBuilder
val InsnListBuilder.ICONST_M1 get() = insn(Opcodes.ICONST_M1)

@InsnBuilder
val InsnListBuilder.ICONST_0 get() = insn(Opcodes.ICONST_0)

@InsnBuilder
val InsnListBuilder.ICONST_1 get() = insn(Opcodes.ICONST_1)

@InsnBuilder
val InsnListBuilder.ICONST_2 get() = insn(Opcodes.ICONST_2)

@InsnBuilder
val InsnListBuilder.ICONST_3 get() = insn(Opcodes.ICONST_3)

@InsnBuilder
val InsnListBuilder.ICONST_4 get() = insn(Opcodes.ICONST_4)

@InsnBuilder
val InsnListBuilder.I2C get() = insn(Opcodes.I2C)

@InsnBuilder
val InsnListBuilder.I2F get() = insn(Opcodes.I2F)

@InsnBuilder
val InsnListBuilder.I2L get() = insn(Opcodes.I2L)

@InsnBuilder
val InsnListBuilder.I2B get() = insn(Opcodes.I2B)

@InsnBuilder
val InsnListBuilder.I2D get() = insn(Opcodes.I2D)

@InsnBuilder
val InsnListBuilder.I2S get() = insn(Opcodes.I2S)

@InsnBuilder
val InsnListBuilder.THIS get() = ALOAD(0)

@InsnBuilder
infix fun InsnListBuilder.GOTO(labelNode: LabelNode) = +JumpInsnNode(GOTO, labelNode)

@InsnBuilder
infix fun InsnListBuilder.IFEQ(labelNode: LabelNode) = +JumpInsnNode(IFEQ, labelNode)

@InsnBuilder
infix fun InsnListBuilder.IFNE(labelNode: LabelNode) = +JumpInsnNode(IFNE, labelNode)

@InsnBuilder
infix fun InsnListBuilder.IFLE(labelNode: LabelNode) = +JumpInsnNode(IFLE, labelNode)

@InsnBuilder
infix fun InsnListBuilder.IFLT(labelNode: LabelNode) = +JumpInsnNode(IFLT, labelNode)

@InsnBuilder
infix fun InsnListBuilder.IFGE(labelNode: LabelNode) = +JumpInsnNode(IFGE, labelNode)

@InsnBuilder
infix fun InsnListBuilder.IFGT(labelNode: LabelNode) = +JumpInsnNode(IFGT, labelNode)

@InsnBuilder
infix fun InsnListBuilder.IF_ICMPEQ(labelNode: LabelNode) = +JumpInsnNode(IF_ICMPEQ, labelNode)

@InsnBuilder
infix fun InsnListBuilder.IF_ICMPNE(labelNode: LabelNode) = +JumpInsnNode(IF_ICMPNE, labelNode)

@InsnBuilder
infix fun InsnListBuilder.IF_ICMPLT(labelNode: LabelNode) = +JumpInsnNode(IF_ICMPLT, labelNode)

@InsnBuilder
infix fun InsnListBuilder.IF_ICMPGE(labelNode: LabelNode) = +JumpInsnNode(IF_ICMPGE, labelNode)

@InsnBuilder
infix fun InsnListBuilder.IF_ICMPGT(labelNode: LabelNode) = +JumpInsnNode(IF_ICMPGT, labelNode)

@InsnBuilder
infix fun InsnListBuilder.IF_ICMPLE(labelNode: LabelNode) = +JumpInsnNode(IF_ICMPLE, labelNode)

@InsnBuilder
infix fun InsnListBuilder.IF_ACMPEQ(labelNode: LabelNode) = +JumpInsnNode(IF_ACMPEQ, labelNode)

@InsnBuilder
infix fun InsnListBuilder.IF_ACMPNE(labelNode: LabelNode) = +JumpInsnNode(IF_ACMPNE, labelNode)

@InsnBuilder
infix fun InsnListBuilder.IFNULL(labelNode: LabelNode) = +JumpInsnNode(IFNULL, labelNode)

@InsnBuilder
infix fun InsnListBuilder.IFNONNULL(labelNode: LabelNode) = +JumpInsnNode(IFNONNULL, labelNode)


@InsnBuilder
infix fun InsnListBuilder.GOTO(label: Label) = +JumpInsnNode(GOTO, getLabelNode(label))

@InsnBuilder
infix fun InsnListBuilder.IFEQ(label: Label) = +JumpInsnNode(IFEQ, getLabelNode(label))

@InsnBuilder
infix fun InsnListBuilder.IFNE(label: Label) = +JumpInsnNode(IFNE, getLabelNode(label))

@InsnBuilder
infix fun InsnListBuilder.IFLE(label: Label) = +JumpInsnNode(IFLE, getLabelNode(label))

@InsnBuilder
infix fun InsnListBuilder.IFLT(label: Label) = +JumpInsnNode(IFLT, getLabelNode(label))

@InsnBuilder
infix fun InsnListBuilder.IFGE(label: Label) = +JumpInsnNode(IFGE, getLabelNode(label))

@InsnBuilder
infix fun InsnListBuilder.IFGT(label: Label) = +JumpInsnNode(IFGT, getLabelNode(label))

@InsnBuilder
infix fun InsnListBuilder.IF_ICMPEQ(label: Label) = +JumpInsnNode(IF_ICMPEQ, getLabelNode(label))

@InsnBuilder
infix fun InsnListBuilder.IF_ICMPNE(label: Label) = +JumpInsnNode(IF_ICMPNE, getLabelNode(label))

@InsnBuilder
infix fun InsnListBuilder.IF_ICMPLT(label: Label) = +JumpInsnNode(IF_ICMPLT, getLabelNode(label))

@InsnBuilder
infix fun InsnListBuilder.IF_ICMPGE(label: Label) = +JumpInsnNode(IF_ICMPGE, getLabelNode(label))

@InsnBuilder
infix fun InsnListBuilder.IF_ICMPGT(label: Label) = +JumpInsnNode(IF_ICMPGT, getLabelNode(label))

@InsnBuilder
infix fun InsnListBuilder.IF_ICMPLE(label: Label) = +JumpInsnNode(IF_ICMPLE, getLabelNode(label))

@InsnBuilder
infix fun InsnListBuilder.IF_ACMPEQ(label: Label) = +JumpInsnNode(IF_ACMPEQ, getLabelNode(label))

@InsnBuilder
infix fun InsnListBuilder.IF_ACMPNE(label: Label) = +JumpInsnNode(IF_ACMPNE, getLabelNode(label))

@InsnBuilder
infix fun InsnListBuilder.IFNULL(label: Label) = +JumpInsnNode(IFNULL, getLabelNode(label))

@InsnBuilder
infix fun InsnListBuilder.IFNONNULL(label: Label) = +JumpInsnNode(IFNONNULL, getLabelNode(label))

@InsnBuilder
fun InsnListBuilder.VAR(type: Int, value: Int) = +VarInsnNode(type, value)

@InsnBuilder
infix fun InsnListBuilder.ASTORE(value: Int) = +VarInsnNode(ASTORE, value)

@InsnBuilder
infix fun InsnListBuilder.ALOAD(value: Int) = +VarInsnNode(ALOAD, value)

@InsnBuilder
infix fun InsnListBuilder.ILOAD(value: Int) = +VarInsnNode(ILOAD, value)

@InsnBuilder
infix fun InsnListBuilder.ISTORE(value: Int) = +VarInsnNode(ISTORE, value)

@InsnBuilder
infix fun InsnListBuilder.FLOAD(value: Int) = +VarInsnNode(FLOAD, value)

@InsnBuilder
infix fun InsnListBuilder.FSTORE(value: Int) = +VarInsnNode(FSTORE, value)

@InsnBuilder
infix fun InsnListBuilder.LLOAD(value: Int) = +VarInsnNode(LLOAD, value)

@InsnBuilder
infix fun InsnListBuilder.LSTORE(value: Int) = +VarInsnNode(LSTORE, value)

@InsnBuilder
infix fun InsnListBuilder.DLOAD(value: Int) = +VarInsnNode(DLOAD, value)

@InsnBuilder
infix fun InsnListBuilder.DSTORE(value: Int) = +VarInsnNode(DSTORE, value)

@InsnBuilder
fun InsnListBuilder.IINC(valueIndex: Int, incr: Int) = +IincInsnNode(valueIndex, incr)

@InsnBuilder
fun InsnListBuilder.INVOKESTATIC(owner: String, name: String, desc: String, isInterface: Boolean = false) =
    +MethodInsnNode(INVOKESTATIC, owner, name, desc, isInterface)

@InsnBuilder
fun InsnListBuilder.INVOKEVIRTUAL(owner: String, name: String, desc: String, isInterface: Boolean = false) =
    +MethodInsnNode(INVOKEVIRTUAL, owner, name, desc, isInterface)

@InsnBuilder
fun InsnListBuilder.INVOKESPECIAL(owner: String, name: String, desc: String, isInterface: Boolean = false) =
    +MethodInsnNode(INVOKESPECIAL, owner, name, desc, isInterface)

@InsnBuilder
fun InsnListBuilder.INVOKEINTERFACE(owner: String, name: String, desc: String, isInterface: Boolean = false) =
    +MethodInsnNode(INVOKEINTERFACE, owner, name, desc, isInterface)

@InsnBuilder
fun InsnListBuilder.INVOKEDYNAMIC(
    name: String,
    descriptor: String,
    bootstrapMethodHandle: Handle,
    vararg bootstrapMethodArguments: Any
) = +InvokeDynamicInsnNode(name, descriptor, bootstrapMethodHandle, *bootstrapMethodArguments)

@InsnBuilder
fun InsnListBuilder.INSN(opcode: Int) = +InsnNode(opcode)

@InsnBuilder
fun InsnListBuilder.GETSTATIC(owner: String, name: String, desc: String) = +FieldInsnNode(GETSTATIC, owner, name, desc)

@InsnBuilder
fun InsnListBuilder.GETFIELD(owner: String, name: String, desc: String) = +FieldInsnNode(GETFIELD, owner, name, desc)

@InsnBuilder
fun InsnListBuilder.PUTSTATIC(owner: String, name: String, desc: String) = +FieldInsnNode(PUTSTATIC, owner, name, desc)

@InsnBuilder
fun InsnListBuilder.PUTFIELD(owner: String, name: String, desc: String) = +FieldInsnNode(PUTFIELD, owner, name, desc)

@InsnBuilder
fun InsnListBuilder.CHECKCAST(type: String) = +TypeInsnNode(CHECKCAST, type)

@InsnBuilder
infix fun InsnListBuilder.NEW(type: String) = +TypeInsnNode(NEW, type)

@InsnBuilder
infix fun InsnListBuilder.NEWARRAY(type: Int) = +IntInsnNode(NEWARRAY, type)

@InsnBuilder
infix fun InsnListBuilder.ANEWARRAY(desc: String) = +TypeInsnNode(ANEWARRAY, desc)

@InsnBuilder
infix fun InsnListBuilder.LDC(int: Int) = +int.toInsnNode()

@InsnBuilder
infix fun InsnListBuilder.LDC(float: Float) = +float.toInsnNode()

@InsnBuilder
infix fun InsnListBuilder.LDC(long: Long) = +long.toInsnNode()

@InsnBuilder
infix fun InsnListBuilder.LDC(double: Double) = +double.toInsnNode()

@InsnBuilder
infix fun InsnListBuilder.LDC(string: String) = +LdcInsnNode(string)

@InsnBuilder
infix fun InsnListBuilder.LDC(handle: Handle) = +LdcInsnNode(handle)

@InsnBuilder
fun InsnListBuilder.FRAME(
    type: Int,
    numLocal: Int,
    local: Array<Any?>?,
    numStack: Int,
    stack: Array<Any?>? = null
) {
    insnList.add(
        FrameNode(
            type,
            numLocal,
            local?.let { getLabelNodes(it) },
            numStack,
            stack?.let { getLabelNodes(it) })
    )
}