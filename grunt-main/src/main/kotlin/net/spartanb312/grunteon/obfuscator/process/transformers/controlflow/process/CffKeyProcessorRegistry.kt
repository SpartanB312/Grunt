package net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.process

import kotlinx.serialization.Serializable
import net.spartanb312.genesis.kotlin.InsnListBuilder
import net.spartanb312.genesis.kotlin.clazz
import net.spartanb312.genesis.kotlin.extensions.INT
import net.spartanb312.genesis.kotlin.extensions.PUBLIC
import net.spartanb312.genesis.kotlin.extensions.STATIC
import net.spartanb312.genesis.kotlin.extensions.SUPER
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunteon.obfuscator.util.GENERATED_CLASS
import net.spartanb312.grunteon.obfuscator.util.GENERATED_METHOD
import net.spartanb312.grunteon.obfuscator.util.extensions.appendAnnotation
import net.spartanb312.grunteon.obfuscator.util.interfaces.DisplayEnum
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.util.concurrent.ConcurrentHashMap

@Serializable
enum class FlowStateKeyMode(override val displayName: CharSequence) : DisplayEnum {
    Inline("Inline"),
    Processor("Processor"),
    Mixed("Mixed")
}

data class FlowStateKeyProcessorCall(
    val owner: String,
    val name: String,
    val desc: String,
    val inputKey: Int,
    val salt: Int
)

interface FlowStateKeyProcessor {
    fun reserve(siteId: Int, inputKey: Int, targetKey: Int, random: UniformRandomProvider): FlowStateKeyProcessorCall
}

data class CffKeyProcessorOptions(
    val minMainSteps: Int = 1,
    val maxMainSteps: Int = 3,
    val minExtraSteps: Int = 0,
    val maxExtraSteps: Int = 1,
    val minChainSteps: Int = 1,
    val maxChainSteps: Int = 2
) {
    val mainStepRange: IntRange
        get() = normalizedIntRange(minMainSteps, maxMainSteps, minimum = 1)

    val extraStepRange: IntRange
        get() = normalizedIntRange(minExtraSteps, maxExtraSteps, minimum = 0)

    val chainStepRange: IntRange
        get() = normalizedIntRange(minChainSteps, maxChainSteps, minimum = 0)
}

class CffKeyProcessorRegistry(
    private val classMarker: String,
    private val classExists: (String) -> Boolean,
    private val options: CffKeyProcessorOptions = CffKeyProcessorOptions()
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
    ): FlowStateKeyProcessor {
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
        var name = "$owner\$KeyProcessor\$$classMarker"
        var index = 0
        while (classExists(name)) {
            index++
            name = "$owner\$KeyProcessor\$$classMarker\$$index"
        }
        return name
    }

    private class MethodProcessor(
        private val registry: CffKeyProcessorRegistry,
        private val owner: String,
        private val ownerVersion: Int,
        private val methodMarker: String,
        private val options: CffKeyProcessorOptions
    ) : FlowStateKeyProcessor {
        override fun reserve(
            siteId: Int,
            inputKey: Int,
            targetKey: Int,
            random: UniformRandomProvider
        ): FlowStateKeyProcessorCall {
            val salt = nextNonZeroInt(random)
            val action = ProcessorActionPlan.create(
                name = "action_${methodMarker}_${siteId.toString(36)}",
                inputKey = inputKey,
                targetKey = targetKey,
                salt = salt,
                options = options,
                random = random
            )
            return registry.processorClassPlan(owner, ownerVersion).add(action)
        }
    }

    private class ProcessorClassPlan(
        val name: String,
        private val version: Int
    ) {
        private val actions = ConcurrentHashMap<String, ProcessorActionPlan>()

        val actionCount: Int
            get() = actions.size

        val isEmpty: Boolean
            get() = actions.isEmpty()

        @Synchronized
        fun add(action: ProcessorActionPlan): FlowStateKeyProcessorCall {
            var candidate = action
            var suffix = 0
            while (actions.containsKey(candidate.name)) {
                suffix++
                candidate = action.copy(name = "${action.name}_$suffix")
            }
            actions[candidate.name] = candidate
            return FlowStateKeyProcessorCall(
                owner = name,
                name = candidate.name,
                desc = ActionDesc,
                inputKey = candidate.inputKey,
                salt = candidate.salt
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

    private data class ProcessorActionPlan(
        val name: String,
        val inputKey: Int,
        val targetKey: Int,
        val salt: Int,
        val keyDecoder: IntConstChain,
        val saltDecoder: IntConstChain,
        val steps: List<ActionStep>,
        val correction: FinalCorrection
    ) {
        fun toMethodNode(): MethodNode {
            return method(
                PUBLIC + STATIC,
                name,
                ActionDesc
            ) {
                INSTRUCTIONS {
                    ILOAD(0)
                    keyDecoder.emit(this)
                    IXOR
                    ISTORE(0)
                    ILOAD(1)
                    saltDecoder.emit(this)
                    IXOR
                    ISTORE(1)
                    ILOAD(0)
                    steps.forEach { it.emit(this) }
                    correction.emit(this)
                    IRETURN
                }
                MAXS(8, 2)
            }.appendAnnotation(GENERATED_METHOD)
        }

        companion object {
            fun create(
                name: String,
                inputKey: Int,
                targetKey: Int,
                salt: Int,
                options: CffKeyProcessorOptions,
                random: UniformRandomProvider
            ): ProcessorActionPlan {
                val keyDecoder = IntConstChain.random(random, options.chainStepRange)
                val saltDecoder = IntConstChain.random(random, options.chainStepRange)
                val encodedInputKey = inputKey xor keyDecoder.value
                val encodedSalt = salt xor saltDecoder.value
                val steps = createSteps(random, options)
                val prefix = steps.fold(inputKey) { value, step -> step.apply(value, salt) }
                val correction = FinalCorrection.create(prefix, targetKey, options, random)
                require(correction.apply(prefix) == targetKey) {
                    "Generated key processor action does not reach target key"
                }
                return ProcessorActionPlan(
                    name = name,
                    inputKey = encodedInputKey,
                    targetKey = targetKey,
                    salt = encodedSalt,
                    keyDecoder = keyDecoder,
                    saltDecoder = saltDecoder,
                    steps = steps,
                    correction = correction
                )
            }

            private fun createSteps(
                random: UniformRandomProvider,
                options: CffKeyProcessorOptions
            ): List<ActionStep> {
                val chainRange = options.chainStepRange
                val mainCount = random.nextCount(options.mainStepRange)
                val extraCount = random.nextCount(options.extraStepRange)
                val steps = mutableListOf<ActionStep>()
                repeat(mainCount) {
                    steps += createMainStep(random, chainRange)
                }
                repeat(extraCount) {
                    steps += createExtraStep(random, chainRange)
                }
                return steps.shuffled(random)
            }

            private fun createMainStep(random: UniformRandomProvider, chainRange: IntRange): ActionStep {
                return when (random.nextInt(5)) {
                    0 -> ActionStep.XorSalt
                    1 -> ActionStep.AddSaltRotate(1 + random.nextInt(31))
                    2 -> ActionStep.XorSaltMul(nextOddInt(random))
                    3 -> ActionStep.RotateBySalt
                    else -> ActionStep.AddSaltChainMul(IntConstChain.random(random, chainRange), nextOddInt(random))
                }
            }

            private fun createExtraStep(random: UniformRandomProvider, chainRange: IntRange): ActionStep {
                return when (random.nextInt(5)) {
                    0 -> ActionStep.AddConst(IntConstChain.random(random, chainRange))
                    1 -> ActionStep.XorConst(IntConstChain.random(random, chainRange))
                    2 -> ActionStep.MulConst(IntConstChain.endingAt(nextOddInt(random), chainRange, random))
                    3 -> ActionStep.XorSaltChain(IntConstChain.random(random, chainRange))
                    else -> ActionStep.AddSaltRotate(1 + random.nextInt(31))
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

    private sealed interface ActionStep {
        fun apply(value: Int, salt: Int): Int

        fun emit(builder: InsnListBuilder)

        data object XorSalt : ActionStep {
            override fun apply(value: Int, salt: Int): Int = value xor salt

            override fun emit(builder: InsnListBuilder) = with(builder) {
                ILOAD(1)
                IXOR
                Unit
            }
        }

        data class AddConst(val value: IntConstChain) : ActionStep {
            override fun apply(value: Int, salt: Int): Int = value + this.value.value

            override fun emit(builder: InsnListBuilder) = with(builder) {
                value.emit(this)
                IADD
                Unit
            }
        }

        data class XorConst(val value: IntConstChain) : ActionStep {
            override fun apply(value: Int, salt: Int): Int = value xor this.value.value

            override fun emit(builder: InsnListBuilder) = with(builder) {
                value.emit(this)
                IXOR
                Unit
            }
        }

        data class MulConst(val value: IntConstChain) : ActionStep {
            override fun apply(value: Int, salt: Int): Int = value * this.value.value

            override fun emit(builder: InsnListBuilder) = with(builder) {
                value.emit(this)
                IMUL
                Unit
            }
        }

        data class AddSaltRotate(val bits: Int) : ActionStep {
            override fun apply(value: Int, salt: Int): Int = value + Integer.rotateLeft(salt, bits)

            override fun emit(builder: InsnListBuilder) = with(builder) {
                ILOAD(1)
                INT(bits)
                INVOKESTATIC("java/lang/Integer", "rotateLeft", "(II)I")
                IADD
                Unit
            }
        }

        data class XorSaltMul(val multiplier: Int) : ActionStep {
            override fun apply(value: Int, salt: Int): Int = value xor (salt * multiplier)

            override fun emit(builder: InsnListBuilder) = with(builder) {
                ILOAD(1)
                INT(multiplier)
                IMUL
                IXOR
                Unit
            }
        }

        data object RotateBySalt : ActionStep {
            override fun apply(value: Int, salt: Int): Int = Integer.rotateLeft(value, salt and 31)

            override fun emit(builder: InsnListBuilder) = with(builder) {
                ILOAD(1)
                INT(31)
                IAND
                INVOKESTATIC("java/lang/Integer", "rotateLeft", "(II)I")
                Unit
            }
        }

        data class XorSaltChain(val mask: IntConstChain) : ActionStep {
            override fun apply(value: Int, salt: Int): Int = value xor (salt xor mask.value)

            override fun emit(builder: InsnListBuilder) = with(builder) {
                ILOAD(1)
                mask.emit(this)
                IXOR
                IXOR
                Unit
            }
        }

        data class AddSaltChainMul(val mask: IntConstChain, val multiplier: Int) : ActionStep {
            override fun apply(value: Int, salt: Int): Int = value + ((salt + mask.value) * multiplier)

            override fun emit(builder: InsnListBuilder) = with(builder) {
                ILOAD(1)
                mask.emit(this)
                IADD
                INT(multiplier)
                IMUL
                IADD
                Unit
            }
        }
    }

    private sealed interface FinalCorrection {
        fun apply(value: Int): Int

        fun emit(builder: InsnListBuilder)

        data class Add(val value: IntConstChain) : FinalCorrection {
            override fun apply(value: Int): Int = value + this.value.value

            override fun emit(builder: InsnListBuilder) = with(builder) {
                value.emit(this)
                IADD
                Unit
            }
        }

        data class Sub(val value: IntConstChain) : FinalCorrection {
            override fun apply(value: Int): Int = value - this.value.value

            override fun emit(builder: InsnListBuilder) = with(builder) {
                value.emit(this)
                ISUB
                Unit
            }
        }

        data class Xor(val value: IntConstChain) : FinalCorrection {
            override fun apply(value: Int): Int = value xor this.value.value

            override fun emit(builder: InsnListBuilder) = with(builder) {
                value.emit(this)
                IXOR
                Unit
            }
        }

        companion object {
            fun create(
                current: Int,
                target: Int,
                options: CffKeyProcessorOptions,
                random: UniformRandomProvider
            ): FinalCorrection {
                return when (random.nextInt(3)) {
                    0 -> Add(IntConstChain.endingAt(target - current, options.chainStepRange, random))
                    1 -> Sub(IntConstChain.endingAt(current - target, options.chainStepRange, random))
                    else -> Xor(IntConstChain.endingAt(current xor target, options.chainStepRange, random))
                }
            }
        }
    }

    companion object {
        const val ActionDesc = "(II)I"
    }
}
