package net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number

import kotlinx.serialization.Serializable
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.*
import net.spartanb312.grunteon.obfuscator.util.collection.FastObjectArrayList
import net.spartanb312.grunteon.obfuscator.util.collection.shuffle
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.methodFullDesc
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.isExcluded
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import net.spartanb312.grunteon.obfuscator.util.numerical.asInt
import net.spartanb312.grunteon.obfuscator.util.numerical.asLong
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode

/**
 * Last update on 2026/03/25 by FluixCarvin
 */
@Transformer.Stability(StableLevel.RockSolid)
@Transformer.Description(
    "process.encrypt.number.number_basic_encrypt.desc",
    "Encrypt numbers via some basic methods"
)
class NumberBasicEncrypt : Transformer<NumberBasicEncrypt.Config>(
    "NumberBasicEncrypt",
    Category.Encryption,
) {

    // TODO: hide chances when disabled
    @Serializable
    data class Config(
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Encrypt integers")
        @SettingName("Integer")
        val integer: Boolean = true,
        @SettingDesc("Integer encrypt rate.")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Integer chance")
        val integerChance: Decimal = 1.0.toDecimal(),
        @SettingDesc("Encrypt longs")
        @SettingName("Long")
        val long: Boolean = true,
        @SettingDesc("Long encrypt rate.")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Long chance")
        val longChance: Decimal = 1.0.toDecimal(),
        @SettingDesc("Encrypt floats")
        @SettingName("Float")
        val float: Boolean = true,
        @SettingDesc("Float encrypt rate.")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Float chance")
        val floatChance: Decimal = 1.0.toDecimal(),
        @SettingDesc("Encrypt doubles")
        @SettingName("Double")
        val double: Boolean = true,
        @SettingDesc("Double encrypt rate.")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Double chance")
        val doubleChance: Decimal = 1.0.toDecimal(),
        @SettingDesc("The upper limit of instruction count for a Method. Typically, each instruction occupies 2-3 bytes, and the upper limit for each Method is 65536 bytes")
        @SettingName("Max instructions")
        val maxInstructions: Int = 16384,
        @SettingDesc("When enabled, a modifier will be applied to all chances. Modifier = (MaxInsn - CurrentInsn) / MaxInsn")
        @SettingName("Dynamic strength")
        val dynamicStrength: Boolean = true,
        @SettingDesc("Specify method exclusions.")
        @SettingName("Exclusion")
        val exclusion: List<String> = listOf(
            "net/dummy/**",
            "net/dummy/Class",
            "net/dummy/Class.method",
            "net/dummy/Class.method()V"
        )
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        val methodExPredicate = globalScopeValue {
            buildMethodNamePredicates(config.exclusion)
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        val shuffledListCache = localScopeValue { FastObjectArrayList<AbstractInsnNode>() }
        parForEachClassesFiltered(
            instance.globalExclusion
                .and(instance.mixinExclusion)
                .and(config.classFilter.toClassPredicate())
        ) { classNode ->
            val counter = counter.local
            if (classNode.isExcluded(DISABLE_NUMBER_ENCRYPT)) return@parForEachClassesFiltered
            val methodExPredicate = methodExPredicate.global
            classNode.methods.asSequence()
                .filter { !it.isAbstract && !it.isNative }
                .forEach { method ->
                    if (method.isExcluded(DISABLE_NUMBER_ENCRYPT)) return@forEach
                    val excluded = methodExPredicate.matchedAnyBy(methodFullDesc(classNode, method))
                    if (excluded) return@forEach
                    if ((method.instructions?.size() ?: 0) >= config.maxInstructions) return@forEach
                    val chanceModifier =
                        (if (config.dynamicStrength) (config.maxInstructions - method.instructions.size()).toFloat() / config.maxInstructions
                        else 1f).coerceIn(0f, 1f)

                    val randomGen = Xoshiro256PPRandom(getSeed(classNode.name, method.name, method.desc))
                    val shuffledList = shuffledListCache.local
                    shuffledList.clearFast()

                    method.instructions.filterTo(shuffledList) { it.opcode != Opcodes.NEWARRAY }
                    shuffledList.shuffle(randomGen)
                    shuffledList.forEach { instruction ->
                        // Encrypt integer
                        if (config.integer && randomGen.nextFloat() < chanceModifier * config.integerChance.toFloat()) {
                            if (instruction.opcode in Opcodes.ICONST_M1..Opcodes.ICONST_5) {
                                val value = instruction.opcode - Opcodes.ICONST_0
                                method.instructions.insertBefore(instruction, randomGen.encrypt(value))
                                method.instructions.remove(instruction)
                                counter.add()
                            } else if (instruction is IntInsnNode) {
                                method.instructions.insertBefore(
                                    instruction,
                                    randomGen.encrypt(instruction.operand)
                                )
                                method.instructions.remove(instruction)
                                counter.add()
                            } else if (instruction is LdcInsnNode && instruction.cst is Int) {
                                val value = instruction.cst as Int
                                if (value < Int.MAX_VALUE - Short.MAX_VALUE * 8) {
                                    method.instructions.insertBefore(instruction, randomGen.encrypt(value))
                                    method.instructions.remove(instruction)
                                    counter.add()
                                }
                            }
                        }
                        // Encrypt long
                        if (config.long && randomGen.nextFloat() < chanceModifier * config.longChance.toFloat()) {
                            if (instruction.opcode in Opcodes.LCONST_0..Opcodes.LCONST_1) {
                                val value = (instruction.opcode - Opcodes.LCONST_0).toLong()
                                method.instructions.insertBefore(instruction, randomGen.encrypt(value))
                                method.instructions.remove(instruction)
                                counter.add()
                            } else if (instruction is LdcInsnNode && instruction.cst is Long) {
                                val value = instruction.cst as Long
                                method.instructions.insertBefore(instruction, randomGen.encrypt(value))
                                method.instructions.remove(instruction)
                                counter.add()
                            }
                        }
                        // Encrypt float
                        if (config.float && randomGen.nextFloat() < chanceModifier * config.floatChance.toFloat()) {
                            fun encryptFloat(float: Float) {
                                method.instructions.insertBefore(instruction, randomGen.encrypt(float))
                                method.instructions.remove(instruction)
                                counter.add()
                            }
                            when {
                                instruction.opcode == Opcodes.FCONST_0 -> encryptFloat(0f)
                                instruction.opcode == Opcodes.FCONST_1 -> encryptFloat(1f)
                                instruction.opcode == Opcodes.FCONST_2 -> encryptFloat(2f)
                                instruction is LdcInsnNode && instruction.cst is Float -> encryptFloat(instruction.cst as Float)
                            }
                        }
                        // Encrypt double
                        if (config.double && randomGen.nextFloat() < chanceModifier * config.doubleChance.toFloat()) {
                            fun encryptDouble(double: Double) {
                                method.instructions.insertBefore(instruction, randomGen.encrypt(double))
                                method.instructions.remove(instruction)
                                counter.add()
                            }
                            when {
                                instruction.opcode == Opcodes.DCONST_0 -> encryptDouble(0.0)
                                instruction.opcode == Opcodes.DCONST_1 -> encryptDouble(1.0)
                                instruction is LdcInsnNode && instruction.cst is Double -> encryptDouble(instruction.cst as Double)
                            }
                        }
                    }
                }
        }
        post {
            Logger.info(" - NumberBasicEncrypt:")
            credit.add(counter.global.get() * 100L)
            Logger.info("    Encrypted ${counter.global.get()} numbers")
        }
    }

    fun UniformRandomProvider.encrypt(value: Float): InsnList {
        return instructions {
            +encrypt(value.asInt())
            INVOKESTATIC("java/lang/Float", "intBitsToFloat", "(I)F")
        }
    }

    fun UniformRandomProvider.encrypt(value: Double): InsnList {
        return instructions {
            +encrypt(value.asLong())
            INVOKESTATIC("java/lang/Double", "longBitsToDouble", "(J)D")
        }
    }

    fun UniformRandomProvider.encrypt(value: Int): InsnList {
        val random = nextInt(Int.MAX_VALUE)
        val negative = (if (nextBoolean()) random else -random) + value
        val obfuscated = value xor negative
        return instructions {
            if (nextBoolean()) {
                INT(negative)
                I2L
                INT(obfuscated)
                I2L
                LXOR
                L2I
            } else {
                LDC(negative.toLong())
                L2I
                INT(obfuscated)
                IXOR
            }
        }
    }

    fun UniformRandomProvider.encrypt(value: Long): InsnList = instructions {
        val key = nextLong()
        val unsignedString = java.lang.Long.toUnsignedString(key, 32)
        LDC(unsignedString)
        INT(32)
        INVOKESTATIC("java/lang/Long", "parseUnsignedLong", "(Ljava/lang/String;I)J")
        val obfuscated = key xor value
        LONG(obfuscated)
        LXOR
    }

}
