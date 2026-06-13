package net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.junkcode

import net.spartanb312.grunt.ir.flow.core.FlowBlock
import net.spartanb312.grunt.ir.flow.core.FlowBlockId
import net.spartanb312.grunt.ir.flow.core.FlowBlockKind
import net.spartanb312.grunt.ir.flow.core.FlowBytecodeSlice
import net.spartanb312.grunt.ir.flow.core.FlowFrame
import net.spartanb312.grunt.ir.flow.core.FlowFrameValue
import net.spartanb312.grunt.ir.flow.core.FlowJumpInput
import net.spartanb312.grunt.ir.flow.core.FlowMethod
import net.spartanb312.grunt.ir.flow.core.FlowReturnJump
import net.spartanb312.grunt.ir.flow.core.FlowThrowJump
import net.spartanb312.grunt.ir.flow.core.categorySize
import net.spartanb312.grunteon.obfuscator.process.hierarchy.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.util.IGNORE_JUNK_CODE
import net.spartanb312.grunteon.obfuscator.util.ANTI_LLM_JUNK_CALL
import net.spartanb312.grunteon.obfuscator.util.extensions.hasAnnotation
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isInterface
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.isPublic
import net.spartanb312.grunteon.obfuscator.util.extensions.isStatic
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TypeInsnNode

data class JunkCodeOptions(
    val maxPreludeCalls: Int = 2,
    val useJunkCallPrelude: Boolean = true,
    val useNaturalReferenceValues: Boolean = true,
    val useAssignableJunkReturns: Boolean = true,
    val junkReturnChance: Double = 0.35,
    val terminalThrowChance: Double = 0.0
)

private val primitiveSorts = setOf(
    Type.BOOLEAN,
    Type.BYTE,
    Type.CHAR,
    Type.SHORT,
    Type.INT,
    Type.LONG,
    Type.FLOAT,
    Type.DOUBLE
)
private val intLikeSorts = setOf(Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT)
private val referenceSorts = setOf(Type.ARRAY, Type.OBJECT)
private val arraySuperTypes = setOf("java/lang/Object", "java/lang/Cloneable", "java/io/Serializable")
private val safeStringArgumentTypes = setOf("java/lang/String", "java/lang/Object", "java/lang/CharSequence")

typealias JunkStringProvider = (UniformRandomProvider) -> String?

fun junkStringProvider(values: List<String>): JunkStringProvider? {
    return if (values.isEmpty()) {
        null
    } else {
        { random -> values[random.nextInt(values.size)] }
    }
}

class JunkCodeGenerator(
    private val callPool: JunkCallPool,
    private val hierarchy: ClassHierarchy,
    private val options: JunkCodeOptions,
    private val random: UniformRandomProvider,
    private val stringProvider: JunkStringProvider? = null
) {
    fun createTerminalBlock(id: FlowBlockId, method: FlowMethod, entryFrame: FlowFrame): FlowBlock {
        val body = FlowBytecodeSlice(mutableListOf())
        dropStack(entryFrame.stack, body.instructions)
        emitPrelude(body.instructions)

        if (random.nextDouble() < options.terminalThrowChance.coerceIn(0.0, 1.0)) {
            return FlowBlock(
                id = id,
                kind = FlowBlockKind.Junk,
                body = body,
                jump = FlowThrowJump(
                    FlowJumpInput.Generated(
                        FlowBytecodeSlice(mutableListOf(InsnNode(Opcodes.ACONST_NULL))),
                        listOf(FlowFrameValue.Null)
                    )
                ),
                entryFrame = entryFrame,
                bodyExitFrame = entryFrame.copy(stack = emptyList())
            )
        }

        val returnType = Type.getReturnType(method.desc)
        val jumpInput = returnInput(returnType)
        return FlowBlock(
            id = id,
            kind = FlowBlockKind.Junk,
            body = body,
            jump = FlowReturnJump(jumpInput),
            entryFrame = entryFrame,
            bodyExitFrame = entryFrame.copy(stack = emptyList())
        )
    }

    fun appendStackNeutralJunk(body: FlowBytecodeSlice, minimumCalls: Int = 1) {
        emitPrelude(body.instructions, minimumCalls)
    }

    private fun returnInput(returnType: Type): FlowJumpInput {
        if (returnType.sort == Type.VOID) return FlowJumpInput.None
        val value = generateReturnValue(returnType)
        return FlowJumpInput.Generated(value.code, listOf(value.frameValue))
    }

    private fun generateReturnValue(returnType: Type): GeneratedValue {
        if (options.useAssignableJunkReturns && random.nextDouble() < options.junkReturnChance) {
            callPool.findReturnFor(returnType, hierarchy, random)?.let {
                return generateCallValue(it)
            }
        }

        return when (returnType.sort) {
            Type.BOOLEAN -> GeneratedValue(FlowBytecodeSlice(mutableListOf(pushInt(random.nextInt(2)))), FlowFrameValue.Int)
            Type.BYTE -> GeneratedValue(FlowBytecodeSlice(mutableListOf(pushInt(naturalByte().toInt()))), FlowFrameValue.Int)
            Type.CHAR -> GeneratedValue(FlowBytecodeSlice(mutableListOf(pushInt(naturalChar().code))), FlowFrameValue.Int)
            Type.SHORT -> GeneratedValue(FlowBytecodeSlice(mutableListOf(pushInt(naturalShort().toInt()))), FlowFrameValue.Int)
            Type.INT -> GeneratedValue(FlowBytecodeSlice(mutableListOf(pushInt(naturalInt()))), FlowFrameValue.Int)
            Type.LONG -> GeneratedValue(FlowBytecodeSlice(mutableListOf(pushLong(naturalLong()))), FlowFrameValue.Long)
            Type.FLOAT -> GeneratedValue(FlowBytecodeSlice(mutableListOf(pushFloat(naturalFloat()))), FlowFrameValue.Float)
            Type.DOUBLE -> GeneratedValue(FlowBytecodeSlice(mutableListOf(pushDouble(naturalDouble()))), FlowFrameValue.Double)
            Type.ARRAY,
            Type.OBJECT -> generateReferenceValue(returnType)
            else -> GeneratedValue(FlowBytecodeSlice(mutableListOf(InsnNode(Opcodes.ACONST_NULL))), FlowFrameValue.Null)
        }
    }

    private fun generateReferenceValue(expectedType: Type): GeneratedValue {
        if (!options.useNaturalReferenceValues) return nullReference()

        val producers = mutableListOf<() -> GeneratedValue>()
        if (expectedType.sort == Type.ARRAY) {
            producers += { generateEmptyArray(expectedType) ?: nullReference() }
        }
        if (isReferenceAssignable(Type.getObjectType("java/lang/String"), expectedType)) {
            producers += { stringValue() }
        }
        if (isReferenceAssignable(Type.getObjectType("java/lang/Boolean"), expectedType)) {
            producers += { booleanWrapperValue() }
        }
        if (isReferenceAssignable(Type.getObjectType("java/lang/Integer"), expectedType)) {
            producers += { boxedValue("java/lang/Integer", "(I)Ljava/lang/Integer;", pushInt(naturalInt())) }
        }
        if (isReferenceAssignable(Type.getObjectType("java/lang/Long"), expectedType)) {
            producers += { boxedValue("java/lang/Long", "(J)Ljava/lang/Long;", pushLong(naturalLong())) }
        }
        if (isReferenceAssignable(Type.getObjectType("java/lang/Float"), expectedType)) {
            producers += { boxedValue("java/lang/Float", "(F)Ljava/lang/Float;", pushFloat(naturalFloat())) }
        }
        if (isReferenceAssignable(Type.getObjectType("java/lang/Double"), expectedType)) {
            producers += { boxedValue("java/lang/Double", "(D)Ljava/lang/Double;", pushDouble(naturalDouble())) }
        }
        if (isReferenceAssignable(Type.getType("[I"), expectedType)) {
            producers += { generateEmptyArray(Type.getType("[I")) ?: nullReference() }
        }

        return if (producers.isEmpty()) nullReference() else producers[random.nextInt(producers.size)].invoke()
    }

    private fun emitPrelude(instructions: MutableList<AbstractInsnNode>, minimumCalls: Int = 0) {
        if (!options.useJunkCallPrelude || options.maxPreludeCalls <= 0) return
        val minCalls = minimumCalls.coerceIn(0, options.maxPreludeCalls)
        val callCount = minCalls + random.nextInt(options.maxPreludeCalls - minCalls + 1)
        repeat(callCount) {
            val call = callPool.randomPreludeCall(random) ?: return
            emitCall(call, instructions)
            emitPop(call.returnType, instructions)
        }
    }

    private fun generateCallValue(call: JunkCall): GeneratedValue {
        val instructions = mutableListOf<AbstractInsnNode>()
        emitCall(call, instructions)
        return GeneratedValue(
            code = FlowBytecodeSlice(instructions),
            frameValue = frameValue(call.returnType)
        )
    }

    private fun emitCall(call: JunkCall, instructions: MutableList<AbstractInsnNode>) {
        call.argumentTypes.forEach { emitArgument(it, instructions) }
        instructions += MethodInsnNode(
            Opcodes.INVOKESTATIC,
            call.owner,
            call.name,
            call.desc,
            call.ownerIsInterface
        )
    }

    private fun emitArgument(type: Type, instructions: MutableList<AbstractInsnNode>) {
        instructions += when (type.sort) {
            Type.BOOLEAN -> pushInt(random.nextInt(2))
            Type.BYTE -> pushInt(naturalByte().toInt())
            Type.CHAR -> pushInt(naturalChar().code)
            Type.SHORT -> pushInt(naturalShort().toInt())
            Type.INT -> pushInt(naturalInt())
            Type.LONG -> pushLong(naturalLong())
            Type.FLOAT -> pushFloat(naturalFloat())
            Type.DOUBLE -> pushDouble(naturalDouble())
            Type.OBJECT -> {
                if (type.internalName in safeStringArgumentTypes) {
                    LdcInsnNode(nextStringValue())
                } else {
                    error("Unsupported junk call object argument type ${type.descriptor}")
                }
            }
            else -> error("Unsupported junk call argument type ${type.descriptor}")
        }
    }

    private fun dropStack(stack: List<FlowFrameValue>, instructions: MutableList<AbstractInsnNode>) {
        for (value in stack.asReversed()) {
            instructions += InsnNode(if (value.categorySize == 2) Opcodes.POP2 else Opcodes.POP)
        }
    }

    private fun emitPop(type: Type, instructions: MutableList<AbstractInsnNode>) {
        when (type.sort) {
            Type.VOID -> Unit
            Type.LONG,
            Type.DOUBLE -> instructions += InsnNode(Opcodes.POP2)
            else -> instructions += InsnNode(Opcodes.POP)
        }
    }

    private fun stringValue(): GeneratedValue {
        return GeneratedValue(
            FlowBytecodeSlice(mutableListOf(LdcInsnNode(nextStringValue()))),
            FlowFrameValue.Object("java/lang/String")
        )
    }

    private fun nextStringValue(): String {
        val provided = stringProvider?.invoke(random)
        if (!provided.isNullOrEmpty()) return provided
        val values = arrayOf("", "0", "1", "true", "false", "null", "value", "key")
        return values[random.nextInt(values.size)]
    }

    private fun booleanWrapperValue(): GeneratedValue {
        val field = if (random.nextBoolean()) "TRUE" else "FALSE"
        return GeneratedValue(
            FlowBytecodeSlice(
                mutableListOf(
                    FieldInsnNode(
                        Opcodes.GETSTATIC,
                        "java/lang/Boolean",
                        field,
                        "Ljava/lang/Boolean;"
                    )
                )
            ),
            FlowFrameValue.Object("java/lang/Boolean")
        )
    }

    private fun boxedValue(owner: String, desc: String, valueInsn: AbstractInsnNode): GeneratedValue {
        return GeneratedValue(
            FlowBytecodeSlice(
                mutableListOf(
                    valueInsn,
                    MethodInsnNode(Opcodes.INVOKESTATIC, owner, "valueOf", desc, false)
                )
            ),
            FlowFrameValue.Object(owner)
        )
    }

    private fun generateEmptyArray(type: Type): GeneratedValue? {
        if (type.dimensions > 255) return null
        val instructions = mutableListOf<AbstractInsnNode>()
        if (type.dimensions == 1) {
            instructions += pushInt(0)
            val element = type.elementType
            if (element.sort in primitiveSorts) {
                instructions += IntInsnNode(Opcodes.NEWARRAY, newArrayType(element.sort))
            } else {
                instructions += TypeInsnNode(Opcodes.ANEWARRAY, element.internalName)
            }
        } else {
            repeat(type.dimensions) {
                instructions += pushInt(0)
            }
            instructions += MultiANewArrayInsnNode(type.descriptor, type.dimensions)
        }
        return GeneratedValue(FlowBytecodeSlice(instructions), frameValue(type))
    }

    private fun nullReference(): GeneratedValue {
        return GeneratedValue(FlowBytecodeSlice(mutableListOf(InsnNode(Opcodes.ACONST_NULL))), FlowFrameValue.Null)
    }

    private fun isReferenceAssignable(actual: Type, expected: Type): Boolean {
        if (actual.sort !in referenceSorts || expected.sort !in referenceSorts) return false
        if (expected.sort == Type.OBJECT && expected.internalName == "java/lang/Object") return true
        if (actual.descriptor == expected.descriptor) return true

        if (actual.sort == Type.ARRAY) {
            if (expected.sort == Type.OBJECT && expected.internalName in arraySuperTypes) return true
            if (expected.sort == Type.ARRAY) return actual.descriptor == expected.descriptor
            return false
        }

        if (expected.sort == Type.ARRAY) return false
        return hierarchy.isSubType(actual.internalName, expected.internalName)
    }

    private fun naturalInt(): Int {
        return when (random.nextInt(100)) {
            in 0..54 -> intArrayOf(0, 1, -1, 2)[random.nextInt(4)]
            in 55..79 -> random.nextInt(25) - 8
            in 80..89 -> intArrayOf(3, 4, 5, 8, 10, 16, 31, 32, 64, 100, 127, 255, 256)[random.nextInt(13)]
            in 90..96 -> intArrayOf(10, 100, 1000)[random.nextInt(3)]
            else -> random.nextInt(Short.MAX_VALUE.toInt() + 1) - Short.MAX_VALUE.toInt() / 2
        }
    }

    private fun naturalLong(): Long {
        return when (random.nextInt(100)) {
            in 0..59 -> longArrayOf(0L, 1L, -1L, 2L)[random.nextInt(4)]
            in 60..84 -> (random.nextInt(33) - 16).toLong()
            in 85..96 -> longArrayOf(10L, 32L, 64L, 100L, 1000L)[random.nextInt(5)]
            else -> naturalInt().toLong()
        }
    }

    private fun naturalFloat(): Float {
        return when (random.nextInt(100)) {
            in 0..64 -> floatArrayOf(0f, 1f, -1f, 2f)[random.nextInt(4)]
            in 65..89 -> floatArrayOf(0.5f, -0.5f, 10f, 100f)[random.nextInt(4)]
            else -> (random.nextInt(33) - 16).toFloat()
        }
    }

    private fun naturalDouble(): Double {
        return when (random.nextInt(100)) {
            in 0..64 -> doubleArrayOf(0.0, 1.0, -1.0, 2.0)[random.nextInt(4)]
            in 65..89 -> doubleArrayOf(0.5, -0.5, 10.0, 100.0)[random.nextInt(4)]
            else -> (random.nextInt(33) - 16).toDouble()
        }
    }

    private fun naturalByte(): Byte = naturalInt().coerceIn(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()).toByte()

    private fun naturalShort(): Short = naturalInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    private fun naturalChar(): Char {
        val values = intArrayOf(0, 1, 32, 48, 49, 65, 97, 255)
        return values[random.nextInt(values.size)].toChar()
    }

    private fun frameValue(type: Type): FlowFrameValue {
        return when (type.sort) {
            Type.BOOLEAN,
            Type.BYTE,
            Type.CHAR,
            Type.SHORT,
            Type.INT -> FlowFrameValue.Int
            Type.LONG -> FlowFrameValue.Long
            Type.FLOAT -> FlowFrameValue.Float
            Type.DOUBLE -> FlowFrameValue.Double
            Type.ARRAY -> FlowFrameValue.Object(type.descriptor)
            Type.OBJECT -> FlowFrameValue.Object(type.internalName)
            else -> FlowFrameValue.Unknown(type.descriptor)
        }
    }

    private fun pushInt(value: Int): AbstractInsnNode {
        return when (value) {
            -1 -> InsnNode(Opcodes.ICONST_M1)
            0 -> InsnNode(Opcodes.ICONST_0)
            1 -> InsnNode(Opcodes.ICONST_1)
            2 -> InsnNode(Opcodes.ICONST_2)
            3 -> InsnNode(Opcodes.ICONST_3)
            4 -> InsnNode(Opcodes.ICONST_4)
            5 -> InsnNode(Opcodes.ICONST_5)
            in Byte.MIN_VALUE..Byte.MAX_VALUE -> IntInsnNode(Opcodes.BIPUSH, value)
            in Short.MIN_VALUE..Short.MAX_VALUE -> IntInsnNode(Opcodes.SIPUSH, value)
            else -> LdcInsnNode(value)
        }
    }

    private fun pushLong(value: Long): AbstractInsnNode {
        return when (value) {
            0L -> InsnNode(Opcodes.LCONST_0)
            1L -> InsnNode(Opcodes.LCONST_1)
            else -> LdcInsnNode(value)
        }
    }

    private fun pushFloat(value: Float): AbstractInsnNode {
        return when (value) {
            0f -> InsnNode(Opcodes.FCONST_0)
            1f -> InsnNode(Opcodes.FCONST_1)
            2f -> InsnNode(Opcodes.FCONST_2)
            else -> LdcInsnNode(value)
        }
    }

    private fun pushDouble(value: Double): AbstractInsnNode {
        return when (value) {
            0.0 -> InsnNode(Opcodes.DCONST_0)
            1.0 -> InsnNode(Opcodes.DCONST_1)
            else -> LdcInsnNode(value)
        }
    }

    private fun newArrayType(sort: Int): Int {
        return when (sort) {
            Type.BOOLEAN -> Opcodes.T_BOOLEAN
            Type.CHAR -> Opcodes.T_CHAR
            Type.FLOAT -> Opcodes.T_FLOAT
            Type.DOUBLE -> Opcodes.T_DOUBLE
            Type.BYTE -> Opcodes.T_BYTE
            Type.SHORT -> Opcodes.T_SHORT
            Type.INT -> Opcodes.T_INT
            Type.LONG -> Opcodes.T_LONG
            else -> error("Unsupported primitive array element sort $sort")
        }
    }

    private data class GeneratedValue(
        val code: FlowBytecodeSlice,
        val frameValue: FlowFrameValue
    )

}

data class JunkCall(
    val owner: String,
    val ownerIsInterface: Boolean,
    val name: String,
    val desc: String,
    val argumentTypes: Array<Type>,
    val returnType: Type
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JunkCall) return false
        if (owner != other.owner) return false
        if (ownerIsInterface != other.ownerIsInterface) return false
        if (name != other.name) return false
        if (desc != other.desc) return false
        if (!argumentTypes.contentEquals(other.argumentTypes)) return false
        return returnType == other.returnType
    }

    override fun hashCode(): Int {
        var result = owner.hashCode()
        result = 31 * result + ownerIsInterface.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + desc.hashCode()
        result = 31 * result + argumentTypes.contentHashCode()
        result = 31 * result + returnType.hashCode()
        return result
    }
}

class JunkCallPool private constructor(
    private val calls: List<JunkCall>
) {
    fun isEmpty(): Boolean = calls.isEmpty()

    fun randomPreludeCall(random: UniformRandomProvider): JunkCall? {
        if (calls.isEmpty()) return null
        return calls[random.nextInt(calls.size)]
    }

    fun findReturnFor(expectedType: Type, hierarchy: ClassHierarchy, random: UniformRandomProvider): JunkCall? {
        val candidates = calls.filter { isReturnAssignable(it.returnType, expectedType, hierarchy) }
        if (candidates.isEmpty()) return null
        return candidates[random.nextInt(candidates.size)]
    }

    companion object {
        fun build(classNodes: Collection<ClassNode>): JunkCallPool {
            val calls = linkedSetOf<JunkCall>()
            classNodes.asSequence()
                .filter { it.isPublic }
                .filterNot { it.hasAnnotation(IGNORE_JUNK_CODE) }
                .forEach { classNode ->
                    classNode.methods.asSequence()
                        .filter { it.isJunkCallCandidate() }
                        .forEach { methodNode ->
                            calls += JunkCall(
                                owner = classNode.name,
                                ownerIsInterface = classNode.isInterface,
                                name = methodNode.name,
                                desc = methodNode.desc,
                                argumentTypes = Type.getArgumentTypes(methodNode.desc),
                                returnType = Type.getReturnType(methodNode.desc)
                            )
                        }
                }
            return JunkCallPool(calls.toList())
        }

        private fun MethodNode.isJunkCallCandidate(): Boolean {
            if (!isPublic || !isStatic || isNative || isAbstract) return false
            if (name.startsWith("<")) return false
            if (hasAnnotation(IGNORE_JUNK_CODE)) return false
            val allowStringArguments = hasAnnotation(ANTI_LLM_JUNK_CALL)
            return Type.getArgumentTypes(desc).all {
                it.sort in primitiveSorts ||
                    (allowStringArguments && it.sort == Type.OBJECT && it.internalName in safeStringArgumentTypes)
            }
        }

        private fun isReturnAssignable(actual: Type, expected: Type, hierarchy: ClassHierarchy): Boolean {
            if (expected.sort == Type.VOID) return actual.sort == Type.VOID
            if (expected.sort in intLikeSorts) return actual.sort in intLikeSorts
            if (expected.sort == Type.LONG || expected.sort == Type.FLOAT || expected.sort == Type.DOUBLE) {
                return actual.sort == expected.sort
            }
            if (expected.sort !in referenceSorts || actual.sort !in referenceSorts) {
                return false
            }
            if (expected.sort == Type.OBJECT && expected.internalName == "java/lang/Object") return true
            if (actual.descriptor == expected.descriptor) return true
            if (actual.sort == Type.ARRAY) {
                return expected.sort == Type.OBJECT && expected.internalName in arraySuperTypes
            }
            if (expected.sort == Type.ARRAY) return false
            return hierarchy.isSubType(actual.internalName, expected.internalName)
        }
    }
}
