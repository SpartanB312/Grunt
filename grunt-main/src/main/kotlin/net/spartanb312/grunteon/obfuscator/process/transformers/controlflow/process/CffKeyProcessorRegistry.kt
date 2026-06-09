package net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.process

import kotlinx.serialization.Serializable
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

class CffKeyProcessorRegistry(
    private val classMarker: String,
    private val classExists: (String) -> Boolean
) {
    private val classes = ConcurrentHashMap<String, ProcessorClassPlan>()

    val classCount: Int
        get() = classes.size

    val actionCount: Int
        get() = classes.values.sumOf { it.actionCount }

    fun methodProcessor(
        owner: String,
        ownerVersion: Int,
        methodMarker: String
    ): FlowStateKeyProcessor {
        val plan = classes.computeIfAbsent(owner) {
            ProcessorClassPlan(
                name = processorClassName(owner),
                version = ownerVersion.takeIf { it > 0 } ?: Opcodes.V1_8
            )
        }
        return MethodProcessor(plan, methodMarker)
    }

    fun materialize(): List<ClassNode> {
        return classes.values
            .sortedBy { it.name }
            .map { it.toClassNode() }
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
        private val owner: ProcessorClassPlan,
        private val methodMarker: String
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
                random = random
            )
            return owner.add(action)
        }
    }

    private class ProcessorClassPlan(
        val name: String,
        private val version: Int
    ) {
        private val actions = ConcurrentHashMap<String, ProcessorActionPlan>()

        val actionCount: Int
            get() = actions.size

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
        val rotateSalt: Int,
        val multiplierA: Int,
        val multiplierB: Int,
        val xorConst: Int,
        val finalAdd: Int
    ) {
        fun toMethodNode(): MethodNode {
            return method(
                PUBLIC + STATIC,
                name,
                ActionDesc
            ) {
                INSTRUCTIONS {
                    ILOAD(0)
                    ILOAD(1)
                    IXOR
                    ILOAD(1)
                    INT(rotateSalt)
                    INVOKESTATIC("java/lang/Integer", "rotateLeft", "(II)I")
                    IADD
                    INT(multiplierA)
                    IMUL
                    ILOAD(1)
                    INT(multiplierB)
                    IMUL
                    IXOR
                    ILOAD(1)
                    INT(31)
                    IAND
                    INVOKESTATIC("java/lang/Integer", "rotateLeft", "(II)I")
                    INT(xorConst)
                    IXOR
                    INT(finalAdd)
                    IADD
                    IRETURN
                }
                MAXS(4, 2)
            }.appendAnnotation(GENERATED_METHOD)
        }

        companion object {
            fun create(
                name: String,
                inputKey: Int,
                targetKey: Int,
                salt: Int,
                random: UniformRandomProvider
            ): ProcessorActionPlan {
                val rotateSalt = 1 + random.nextInt(31)
                val multiplierA = nextOddInt(random)
                val multiplierB = nextOddInt(random)
                val xorConst = nextNonZeroInt(random)
                val prefix = applyPrefix(inputKey, salt, rotateSalt, multiplierA, multiplierB, xorConst)
                return ProcessorActionPlan(
                    name = name,
                    inputKey = inputKey,
                    targetKey = targetKey,
                    salt = salt,
                    rotateSalt = rotateSalt,
                    multiplierA = multiplierA,
                    multiplierB = multiplierB,
                    xorConst = xorConst,
                    finalAdd = targetKey - prefix
                )
            }

            private fun applyPrefix(
                inputKey: Int,
                salt: Int,
                rotateSalt: Int,
                multiplierA: Int,
                multiplierB: Int,
                xorConst: Int
            ): Int {
                var value = inputKey xor salt
                value += Integer.rotateLeft(salt, rotateSalt)
                value *= multiplierA
                value = value xor (salt * multiplierB)
                value = Integer.rotateLeft(value, salt and 31)
                value = value xor xorConst
                return value
            }
        }
    }

    companion object {
        const val ActionDesc = "(II)I"

        private fun nextNonZeroInt(random: UniformRandomProvider): Int {
            var value = random.nextInt()
            while (value == 0) value = random.nextInt()
            return value
        }

        private fun nextOddInt(random: UniformRandomProvider): Int {
            return nextNonZeroInt(random) or 1
        }

    }
}
