package net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.process

import net.spartanb312.genesis.kotlin.InsnListBuilder
import net.spartanb312.genesis.kotlin.clazz
import net.spartanb312.genesis.kotlin.extensions.INT
import net.spartanb312.genesis.kotlin.extensions.PUBLIC
import net.spartanb312.genesis.kotlin.extensions.STATIC
import net.spartanb312.genesis.kotlin.extensions.SUPER
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunt.ir.flow.core.FlowBytecodeSlice
import net.spartanb312.grunt.ir.flow.core.FlowFrameValue
import net.spartanb312.grunt.ir.flow.core.FlowJumpInput
import net.spartanb312.grunt.ir.flow.core.FlowPredicateGuarantee
import net.spartanb312.grunteon.obfuscator.util.GENERATED_CLASS
import net.spartanb312.grunteon.obfuscator.util.GENERATED_METHOD
import net.spartanb312.grunteon.obfuscator.util.extensions.appendAnnotation
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.util.concurrent.ConcurrentHashMap

internal data class OpaquePredicateProcessorOptions(
    val minMainSteps: Int = 1,
    val maxMainSteps: Int = 2,
    val minExtraSteps: Int = 0,
    val maxExtraSteps: Int = 1,
    val minChainSteps: Int = 1,
    val maxChainSteps: Int = 2,
    val randomBoundChance: Double = 0.15,
    val randomBoundMinMainSteps: Int = 1,
    val randomBoundMaxMainSteps: Int = 1,
    val randomBoundMinExtraSteps: Int = 0,
    val randomBoundMaxExtraSteps: Int = 0,
    val randomBoundMinChainSteps: Int = 1,
    val randomBoundMaxChainSteps: Int = 1,
    val randomBoundMin: Int = 3,
    val randomBoundMax: Int = 17,
    val randomBoundMaxDelta: Int = 8
) {
    val mainStepRange: IntRange
        get() = normalizedIntRange(minMainSteps, maxMainSteps, minimum = 1)

    val extraStepRange: IntRange
        get() = normalizedIntRange(minExtraSteps, maxExtraSteps, minimum = 0)

    val chainStepRange: IntRange
        get() = normalizedIntRange(minChainSteps, maxChainSteps, minimum = 0)

    val randomBoundMainStepRange: IntRange
        get() = normalizedIntRange(randomBoundMinMainSteps, randomBoundMaxMainSteps, minimum = 1)

    val randomBoundExtraStepRange: IntRange
        get() = normalizedIntRange(randomBoundMinExtraSteps, randomBoundMaxExtraSteps, minimum = 0)

    val randomBoundChainStepRange: IntRange
        get() = normalizedIntRange(randomBoundMinChainSteps, randomBoundMaxChainSteps, minimum = 0)

    val randomBoundRange: IntRange
        get() {
            val start = randomBoundMin.coerceAtLeast(1)
            val end = randomBoundMax.coerceAtLeast(start)
            return start..end
        }

    val normalizedRandomBoundChance: Double
        get() = randomBoundChance.coerceIn(0.0, 1.0)
}

internal sealed interface OpaquePredicateCall {
    val opcode: Int
    val guarantee: FlowPredicateGuarantee

    fun toJumpInput(): FlowJumpInput.Generated
}

internal data class OpaquePredicateProcessorCall(
    val owner: String,
    val name: String,
    val desc: String,
    val left: Int,
    val right: Int,
    val salt: Int,
    val compareValue: IntConstChain,
    override val opcode: Int
) : OpaquePredicateCall {
    override val guarantee: FlowPredicateGuarantee = FlowPredicateGuarantee.AlwaysTrue

    override fun toJumpInput(): FlowJumpInput.Generated {
        return FlowJumpInput.Generated(
            code = FlowBytecodeSlice(
                instructions {
                    INT(left)
                    INT(right)
                    INT(salt)
                    INVOKESTATIC(owner, name, desc)
                    compareValue.emit(this)
                }.toArray().toMutableList()
            ),
            produced = listOf(FlowFrameValue.Int, FlowFrameValue.Int),
            guarantee = FlowPredicateGuarantee.AlwaysTrue
        )
    }
}

internal data class RandomBoundOpaquePredicateProcessorCall(
    val valueAction: PredicateActionInvocation,
    val boundAction: PredicateActionInvocation,
    override val opcode: Int,
    override val guarantee: FlowPredicateGuarantee
) : OpaquePredicateCall {
    override fun toJumpInput(): FlowJumpInput.Generated {
        return FlowJumpInput.Generated(
            code = FlowBytecodeSlice(
                instructions {
                    valueAction.emit(this)
                    INVOKESTATIC(
                        "java/util/concurrent/ThreadLocalRandom",
                        "current",
                        "()Ljava/util/concurrent/ThreadLocalRandom;"
                    )
                    boundAction.emit(this)
                    INVOKEVIRTUAL(
                        "java/util/concurrent/ThreadLocalRandom",
                        "nextInt",
                        "(I)I"
                    )
                }.toArray().toMutableList()
            ),
            produced = listOf(FlowFrameValue.Int, FlowFrameValue.Int),
            guarantee = guarantee
        )
    }
}

internal data class PredicateActionInvocation(
    val owner: String,
    val name: String,
    val desc: String,
    val left: Int,
    val right: Int,
    val salt: Int,
    val result: Int
) {
    fun emit(builder: InsnListBuilder) = with(builder) {
        INT(left)
        INT(right)
        INT(salt)
        INVOKESTATIC(owner, name, desc)
        Unit
    }
}

internal interface FlowOpaquePredicateProcessor {
    fun reserveAlwaysTrue(siteId: Int, random: UniformRandomProvider): OpaquePredicateProcessorCall
    fun reserveGate(siteId: Int, random: UniformRandomProvider): OpaquePredicateCall
}

internal class OpaquePredicateProcessorRegistry(
    private val classMarker: String,
    private val classExists: (String) -> Boolean,
    private val options: OpaquePredicateProcessorOptions = OpaquePredicateProcessorOptions()
) {
    private val classes = ConcurrentHashMap<String, ProcessorClassPlan>()

    val classCount: Int
        get() = classes.values.count { !it.isEmpty }

    val actionCount: Int
        get() = classes.values.sumOf { it.actionCount }

    fun methodProcessor(
        owner: String,
        ownerVersion: Int,
        methodMarker: String
    ): FlowOpaquePredicateProcessor {
        return MethodProcessor(this, owner, ownerVersion, methodMarker, options)
    }

    fun materialize(): List<ClassNode> {
        return classes.values
            .filter { !it.isEmpty }
            .sortedBy { it.name }
            .map { it.toClassNode() }
    }

    private fun processorClassPlan(owner: String, ownerVersion: Int): ProcessorClassPlan {
        return classes.computeIfAbsent(owner) {
            ProcessorClassPlan(
                name = processorClassName(owner),
                version = ownerVersion.takeIf { it > 0 } ?: Opcodes.V1_8
            )
        }
    }

    private fun processorClassName(owner: String): String {
        var name = "$owner\$PredicateProcessor\$$classMarker"
        var index = 0
        while (classExists(name)) {
            index++
            name = "$owner\$PredicateProcessor\$$classMarker\$$index"
        }
        return name
    }

    private class MethodProcessor(
        private val registry: OpaquePredicateProcessorRegistry,
        private val owner: String,
        private val ownerVersion: Int,
        private val methodMarker: String,
        private val options: OpaquePredicateProcessorOptions
    ) : FlowOpaquePredicateProcessor {
        override fun reserveAlwaysTrue(
            siteId: Int,
            random: UniformRandomProvider
        ): OpaquePredicateProcessorCall {
            val action = PredicateActionPlan.create(
                name = "predicate_${methodMarker}_${siteId.toString(36)}",
                options = options,
                random = random
            )
            return registry.processorClassPlan(owner, ownerVersion).add(action)
        }

        override fun reserveGate(siteId: Int, random: UniformRandomProvider): OpaquePredicateCall {
            if (random.nextDouble() >= options.normalizedRandomBoundChance) {
                return reserveAlwaysTrue(siteId, random)
            }
            return reserveRandomBoundGate(siteId, random)
        }

        private fun reserveRandomBoundGate(
            siteId: Int,
            random: UniformRandomProvider
        ): RandomBoundOpaquePredicateProcessorCall {
            val classPlan = registry.processorClassPlan(owner, ownerVersion)
            val boundRange = options.randomBoundRange
            val bound = boundRange.first + random.nextInt(boundRange.last - boundRange.first + 1)
            val delta = 1 + random.nextInt(options.randomBoundMaxDelta.coerceAtLeast(1))
            val value = bound + delta
            val guarantee = if (random.nextBoolean()) {
                FlowPredicateGuarantee.AlwaysTrue
            } else {
                FlowPredicateGuarantee.AlwaysFalse
            }
            val valueAction = classPlan.addInvocation(
                PredicateActionPlan.createConstant(
                    name = "predicate_${methodMarker}_${siteId.toString(36)}_v",
                    target = value,
                    options = options,
                    random = random
                )
            )
            val boundAction = classPlan.addInvocation(
                PredicateActionPlan.createConstant(
                    name = "predicate_${methodMarker}_${siteId.toString(36)}_b",
                    target = bound,
                    options = options,
                    random = random
                )
            )
            return RandomBoundOpaquePredicateProcessorCall(
                valueAction = valueAction,
                boundAction = boundAction,
                opcode = if (guarantee == FlowPredicateGuarantee.AlwaysTrue) {
                    Opcodes.IF_ICMPGT
                } else {
                    Opcodes.IF_ICMPLE
                },
                guarantee = guarantee
            )
        }
    }

    private class ProcessorClassPlan(
        val name: String,
        private val version: Int
    ) {
        private val actions = ConcurrentHashMap<String, PredicateActionPlan>()

        val actionCount: Int
            get() = actions.size

        val isEmpty: Boolean
            get() = actions.isEmpty()

        @Synchronized
        fun add(action: PredicateActionPlan): OpaquePredicateProcessorCall {
            val invocation = addInvocation(action)
            return OpaquePredicateProcessorCall(
                owner = invocation.owner,
                name = invocation.name,
                desc = invocation.desc,
                left = invocation.left,
                right = invocation.right,
                salt = invocation.salt,
                compareValue = action.compareValue,
                opcode = action.comparison.opcode
            )
        }

        @Synchronized
        fun addInvocation(action: PredicateActionPlan): PredicateActionInvocation {
            var candidate = action
            var suffix = 0
            while (actions.containsKey(candidate.name)) {
                suffix++
                candidate = action.copy(name = "${action.name}_$suffix")
            }
            actions[candidate.name] = candidate
            return PredicateActionInvocation(
                owner = name,
                name = candidate.name,
                desc = ActionDesc,
                left = candidate.left,
                right = candidate.right,
                salt = candidate.salt,
                result = candidate.result
            )
        }

        fun toClassNode(): ClassNode {
            return clazz(
                access = PUBLIC + SUPER,
                name = name,
                version = version
            ) {
                actions.values
                    .sortedBy { it.name }
                    .forEach { +it.toMethodNode() }
            }.appendAnnotation(GENERATED_CLASS)
        }
    }

    private data class PredicateActionPlan(
        val name: String,
        val left: Int,
        val right: Int,
        val salt: Int,
        val leftDecoder: IntConstChain,
        val rightDecoder: IntConstChain,
        val saltDecoder: IntConstChain,
        val action: BitAction,
        val actionVariant: Int,
        val steps: List<PredicateStep>,
        val result: Int,
        val comparison: Comparison,
        val compareValue: IntConstChain
    ) {
        fun toMethodNode(): MethodNode {
            return method(
                PUBLIC + STATIC,
                name,
                ActionDesc
            ) {
                INSTRUCTIONS {
                    ILOAD(0)
                    leftDecoder.emit(this)
                    IXOR
                    ISTORE(0)
                    ILOAD(1)
                    rightDecoder.emit(this)
                    IXOR
                    ISTORE(1)
                    ILOAD(2)
                    saltDecoder.emit(this)
                    IXOR
                    ISTORE(2)
                    ILOAD(0)
                    ILOAD(1)
                    action.emit(this, actionVariant)
                    steps.forEach { it.emit(this) }
                    IRETURN
                }
                MAXS(10, 3)
            }.appendAnnotation(GENERATED_METHOD)
        }

        companion object {
            fun create(
                name: String,
                options: OpaquePredicateProcessorOptions,
                random: UniformRandomProvider
            ): PredicateActionPlan {
                val leftDecoder = IntConstChain.random(random, options.chainStepRange)
                val rightDecoder = IntConstChain.random(random, options.chainStepRange)
                val saltDecoder = IntConstChain.random(random, options.chainStepRange)
                val plainLeft = nextNonZeroInt(random)
                val plainRight = nextNonZeroInt(random)
                val plainSalt = nextNonZeroInt(random)
                val action = BitAction.random(random)
                val actionVariant = random.nextInt(4)
                val steps = createSteps(random, options)
                val result = steps.fold(action.apply(plainLeft, plainRight)) { value, step ->
                    step.apply(value, plainSalt)
                }
                val comparison = Comparison.trueAgainst(result, random)
                val compareValue = IntConstChain.endingAt(comparison.value, options.chainStepRange, random)
                require(comparison.test(result, compareValue.value)) {
                    "Generated opaque predicate is not true"
                }
                return PredicateActionPlan(
                    name = name,
                    left = plainLeft xor leftDecoder.value,
                    right = plainRight xor rightDecoder.value,
                    salt = plainSalt xor saltDecoder.value,
                    leftDecoder = leftDecoder,
                    rightDecoder = rightDecoder,
                    saltDecoder = saltDecoder,
                    action = action,
                    actionVariant = actionVariant,
                    steps = steps,
                    result = result,
                    comparison = comparison,
                    compareValue = compareValue
                )
            }

            fun createConstant(
                name: String,
                target: Int,
                options: OpaquePredicateProcessorOptions,
                random: UniformRandomProvider
            ): PredicateActionPlan {
                val chainRange = options.randomBoundChainStepRange
                val leftDecoder = IntConstChain.random(random, chainRange)
                val rightDecoder = IntConstChain.random(random, chainRange)
                val saltDecoder = IntConstChain.random(random, chainRange)
                val plainSalt = nextNonZeroInt(random)
                val steps = createSteps(
                    random = random,
                    mainRange = options.randomBoundMainStepRange,
                    extraRange = options.randomBoundExtraStepRange,
                    chainRange = chainRange
                )
                val base = steps
                    .asReversed()
                    .fold(target) { value, step -> step.inverse(value, plainSalt) }
                val plainLeft = nextNonZeroInt(random)
                val plainRight = plainLeft xor base
                val action = BitAction.Xor
                val actionVariant = random.nextInt(4)
                val result = steps.fold(action.apply(plainLeft, plainRight)) { value, step ->
                    step.apply(value, plainSalt)
                }
                val comparison = Comparison.trueAgainst(result, random)
                val compareValue = IntConstChain.endingAt(comparison.value, chainRange, random)
                require(result == target) {
                    "Generated random-bound predicate action does not reach target"
                }
                return PredicateActionPlan(
                    name = name,
                    left = plainLeft xor leftDecoder.value,
                    right = plainRight xor rightDecoder.value,
                    salt = plainSalt xor saltDecoder.value,
                    leftDecoder = leftDecoder,
                    rightDecoder = rightDecoder,
                    saltDecoder = saltDecoder,
                    action = action,
                    actionVariant = actionVariant,
                    steps = steps,
                    result = result,
                    comparison = comparison,
                    compareValue = compareValue
                )
            }

            private fun createSteps(
                random: UniformRandomProvider,
                options: OpaquePredicateProcessorOptions
            ): List<PredicateStep> {
                return createSteps(
                    random = random,
                    mainRange = options.mainStepRange,
                    extraRange = options.extraStepRange,
                    chainRange = options.chainStepRange
                )
            }

            private fun createSteps(
                random: UniformRandomProvider,
                mainRange: IntRange,
                extraRange: IntRange,
                chainRange: IntRange
            ): List<PredicateStep> {
                val mainCount = random.nextCount(mainRange)
                val extraCount = random.nextCount(extraRange)
                val steps = mutableListOf<PredicateStep>()
                repeat(mainCount) {
                    steps += createMainStep(random, chainRange)
                }
                repeat(extraCount) {
                    steps += createExtraStep(random, chainRange)
                }
                return steps.shuffled(random)
            }

            private fun createMainStep(random: UniformRandomProvider, chainRange: IntRange): PredicateStep {
                return when (random.nextInt(4)) {
                    0 -> PredicateStep.XorSalt
                    1 -> PredicateStep.AddSaltRotate(1 + random.nextInt(31))
                    2 -> PredicateStep.XorSaltMul(nextOddInt(random))
                    else -> PredicateStep.AddSaltChainMul(IntConstChain.random(random, chainRange), nextOddInt(random))
                }
            }

            private fun createExtraStep(random: UniformRandomProvider, chainRange: IntRange): PredicateStep {
                return when (random.nextInt(4)) {
                    0 -> PredicateStep.AddConst(IntConstChain.random(random, chainRange))
                    1 -> PredicateStep.XorConst(IntConstChain.random(random, chainRange))
                    2 -> PredicateStep.MulConst(IntConstChain.endingAt(nextOddInt(random), chainRange, random))
                    else -> PredicateStep.RotateBySalt
                }
            }

            private fun <T> MutableList<T>.shuffled(random: UniformRandomProvider): List<T> {
                for (index in lastIndex downTo 1) {
                    val swapIndex = random.nextInt(index + 1)
                    val value = this[index]
                    this[index] = this[swapIndex]
                    this[swapIndex] = value
                }
                return toList()
            }
        }
    }

    private enum class BitAction {
        And,
        Or,
        Xor;

        fun apply(left: Int, right: Int): Int {
            return when (this) {
                And -> left and right
                Or -> left or right
                Xor -> left xor right
            }
        }

        fun emit(builder: InsnListBuilder, variant: Int) = with(builder) {
            when (this@BitAction) {
                And -> emitAnd()
                Or -> emitOr()
                Xor -> emitXor(variant)
            }
        }

        private fun InsnListBuilder.emitAnd() {
            SWAP
            DUP_X1
            ICONST_M1
            IXOR
            IOR
            SWAP
            ICONST_M1
            IXOR
            ISUB
        }

        private fun InsnListBuilder.emitOr() {
            DUP_X1
            ICONST_M1
            IXOR
            IAND
            IADD
        }

        private fun InsnListBuilder.emitXor(variant: Int) {
            when (variant and 3) {
                0 -> {
                    DUP2
                    IOR
                    DUP_X2
                    POP
                    IAND
                    ISUB
                }
                1 -> {
                    DUP2
                    ICONST_M1
                    IXOR
                    IAND
                    DUP_X2
                    POP
                    SWAP
                    ICONST_M1
                    IXOR
                    IAND
                    IOR
                }
                2 -> {
                    DUP2
                    IOR
                    DUP_X2
                    POP
                    ICONST_M1
                    IXOR
                    SWAP
                    ICONST_M1
                    IXOR
                    IOR
                    IAND
                }
                else -> IXOR
            }
        }

        companion object {
            fun random(random: UniformRandomProvider): BitAction {
                return entries[random.nextInt(entries.size)]
            }
        }
    }

    private sealed interface PredicateStep {
        fun apply(value: Int, salt: Int): Int

        fun inverse(value: Int, salt: Int): Int

        fun emit(builder: InsnListBuilder)

        data object XorSalt : PredicateStep {
            override fun apply(value: Int, salt: Int): Int = value xor salt

            override fun inverse(value: Int, salt: Int): Int = value xor salt

            override fun emit(builder: InsnListBuilder) = with(builder) {
                ILOAD(2)
                IXOR
                Unit
            }
        }

        data class AddConst(val value: IntConstChain) : PredicateStep {
            override fun apply(value: Int, salt: Int): Int = value + this.value.value

            override fun inverse(value: Int, salt: Int): Int = value - this.value.value

            override fun emit(builder: InsnListBuilder) = with(builder) {
                value.emit(this)
                IADD
                Unit
            }
        }

        data class XorConst(val value: IntConstChain) : PredicateStep {
            override fun apply(value: Int, salt: Int): Int = value xor this.value.value

            override fun inverse(value: Int, salt: Int): Int = value xor this.value.value

            override fun emit(builder: InsnListBuilder) = with(builder) {
                value.emit(this)
                IXOR
                Unit
            }
        }

        data class MulConst(val value: IntConstChain) : PredicateStep {
            override fun apply(value: Int, salt: Int): Int = value * this.value.value

            override fun inverse(value: Int, salt: Int): Int = value * PredicateStep.inverseOddInt(this.value.value)

            override fun emit(builder: InsnListBuilder) = with(builder) {
                value.emit(this)
                IMUL
                Unit
            }
        }

        data class AddSaltRotate(val bits: Int) : PredicateStep {
            override fun apply(value: Int, salt: Int): Int = value + Integer.rotateLeft(salt, bits)

            override fun inverse(value: Int, salt: Int): Int = value - Integer.rotateLeft(salt, bits)

            override fun emit(builder: InsnListBuilder) = with(builder) {
                ILOAD(2)
                INT(bits)
                INVOKESTATIC("java/lang/Integer", "rotateLeft", "(II)I")
                IADD
                Unit
            }
        }

        data class XorSaltMul(val multiplier: Int) : PredicateStep {
            override fun apply(value: Int, salt: Int): Int = value xor (salt * multiplier)

            override fun inverse(value: Int, salt: Int): Int = value xor (salt * multiplier)

            override fun emit(builder: InsnListBuilder) = with(builder) {
                ILOAD(2)
                INT(multiplier)
                IMUL
                IXOR
                Unit
            }
        }

        data object RotateBySalt : PredicateStep {
            override fun apply(value: Int, salt: Int): Int = Integer.rotateLeft(value, salt and 31)

            override fun inverse(value: Int, salt: Int): Int = Integer.rotateRight(value, salt and 31)

            override fun emit(builder: InsnListBuilder) = with(builder) {
                ILOAD(2)
                INT(31)
                IAND
                INVOKESTATIC("java/lang/Integer", "rotateLeft", "(II)I")
                Unit
            }
        }

        data class AddSaltChainMul(val mask: IntConstChain, val multiplier: Int) : PredicateStep {
            override fun apply(value: Int, salt: Int): Int = value + ((salt + mask.value) * multiplier)

            override fun inverse(value: Int, salt: Int): Int = value - ((salt + mask.value) * multiplier)

            override fun emit(builder: InsnListBuilder) = with(builder) {
                ILOAD(2)
                mask.emit(this)
                IADD
                INT(multiplier)
                IMUL
                IADD
                Unit
            }
        }

        companion object {
            fun inverseOddInt(value: Int): Int {
                require(value and 1 != 0) { "Only odd int values have a modular inverse" }
                var inverse = value
                repeat(5) {
                    inverse *= 2 - value * inverse
                }
                return inverse
            }
        }
    }

    private data class Comparison(
        val kind: ComparisonKind,
        val value: Int
    ) {
        val opcode: Int
            get() = kind.opcode

        fun test(left: Int, right: Int): Boolean = kind.test(left, right)

        companion object {
            fun trueAgainst(value: Int, random: UniformRandomProvider): Comparison {
                val valid = ComparisonKind.entries.filter { it.canBeTrueAgainst(value) }
                val kind = valid[random.nextInt(valid.size)]
                return Comparison(kind, kind.trueRightOperand(value, random))
            }
        }
    }

    private enum class ComparisonKind(val opcode: Int) {
        Eq(Opcodes.IF_ICMPEQ),
        Ne(Opcodes.IF_ICMPNE),
        Lt(Opcodes.IF_ICMPLT),
        Le(Opcodes.IF_ICMPLE),
        Gt(Opcodes.IF_ICMPGT),
        Ge(Opcodes.IF_ICMPGE);

        fun test(left: Int, right: Int): Boolean {
            return when (this) {
                Eq -> left == right
                Ne -> left != right
                Lt -> left < right
                Le -> left <= right
                Gt -> left > right
                Ge -> left >= right
            }
        }

        fun canBeTrueAgainst(left: Int): Boolean {
            return when (this) {
                Lt -> left != Int.MAX_VALUE
                Gt -> left != Int.MIN_VALUE
                else -> true
            }
        }

        fun trueRightOperand(left: Int, random: UniformRandomProvider): Int {
            return when (this) {
                Eq -> left
                Ne -> nextDifferent(left, random)
                Lt -> nextGreater(left, random)
                Le -> if (left == Int.MAX_VALUE || random.nextBoolean()) left else nextGreater(left, random)
                Gt -> nextLess(left, random)
                Ge -> if (left == Int.MIN_VALUE || random.nextBoolean()) left else nextLess(left, random)
            }
        }

        private fun nextDifferent(value: Int, random: UniformRandomProvider): Int {
            var candidate = random.nextInt()
            while (candidate == value) candidate = random.nextInt()
            return candidate
        }

        private fun nextGreater(value: Int, random: UniformRandomProvider): Int {
            repeat(64) {
                val candidate = random.nextInt()
                if (candidate > value) return candidate
            }
            return value + 1
        }

        private fun nextLess(value: Int, random: UniformRandomProvider): Int {
            repeat(64) {
                val candidate = random.nextInt()
                if (candidate < value) return candidate
            }
            return value - 1
        }
    }

    companion object {
        const val ActionDesc = "(III)I"
    }
}
