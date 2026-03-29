package net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number

import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.config.whenTrue
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.collection.FastObjectArrayList
import net.spartanb312.grunteon.obfuscator.util.collection.shuffle
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.methodFullDesc
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
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
 * Basic number encryption
 * Last update on 2026/03/25 by FluixCarvin
 */
class NumberBasicEncrypt : Transformer<NumberBasicEncrypt.Config>(
    name = enText("process.encrypt.number.number_basic_encrypt", "NumberBasicEncrypt"),
    category = Category.Encryption,
    parallel = true
) {

    override val defConfig: TransformerConfig get() = Config()
    override val confType: Class<Config> get() = Config::class.java

    class Config : TransformerConfig() {
        // Integer
        private val integer0 = setting(
            name = enText("process.encrypt.number.number_basic_encrypt.integer", "Integer"),
            value = true,
            desc = enText("process.encrypt.number.number_basic_encrypt.integer.desc", "Encrypt integers")
        )
        val integer by integer0
        val integerChance by setting(
            name = enText("process.encrypt.number.number_basic_encrypt.integer_chance", "Integer chance"),
            value = 1f,
            range = 0f..1f,
            desc = enText(
                "process.encrypt.number.number_basic_encrypt.integer_chance.desc",
                "Integer encrypt rate. Range: 0.0..1.0"
            )
        ).whenTrue(integer0)

        // Long
        private val long0 = setting(
            name = enText("process.encrypt.number.number_basic_encrypt.long", "Long"),
            value = true,
            desc = enText("process.encrypt.number.number_basic_encrypt.long.desc", "Encrypt longs")
        )
        val long by long0
        val longChance by setting(
            name = enText("process.encrypt.number.number_basic_encrypt.long_chance", "Long chance"),
            value = 1f,
            range = 0f..1f,
            desc = enText(
                "process.encrypt.number.number_basic_encrypt.long_chance.desc",
                "Long encrypt rate. Range: 0.0..1.0"
            )
        ).whenTrue(long0)

        // Float
        val float0 = setting(
            name = enText("process.encrypt.number.number_basic_encrypt.float", "Float"),
            value = true,
            desc = enText("process.encrypt.number.number_basic_encrypt.float.desc", "Encrypt floats")
        )
        val float by float0
        val floatChance by setting(
            name = enText("process.encrypt.number.number_basic_encrypt.float_chance", "Float chance"),
            value = 1f,
            range = 0f..1f,
            desc = enText(
                "process.encrypt.number.number_basic_encrypt.float_chance.desc",
                "Float encrypt rate. Range: 0.0..1.0"
            )
        ).whenTrue(float0)

        // Double
        val double0 = setting(
            name = enText("process.encrypt.number.number_basic_encrypt.double", "Double"),
            value = true,
            desc = enText("process.encrypt.number.number_basic_encrypt.double.desc", "Encrypt doubles")
        )
        val double by double0
        val doubleChance by setting(
            name = enText("process.encrypt.number.number_basic_encrypt.float_chance", "Double chance"),
            value = 1f,
            range = 0f..1f,
            desc = enText(
                "process.encrypt.number.number_basic_encrypt.double_chance.desc",
                "Double encrypt rate. Range: 0.0..1.0"
            )
        ).whenTrue(double0)

        // Dynamic
        val maxInstructions by setting(
            name = enText("process.encrypt.number.number_basic_encrypt.max_instructions", "Max instructions"),
            value = 16384,
            desc = enText(
                "process.encrypt.number.number_basic_encrypt.max_instruction.desc",
                "The upper limit of instruction count for a Method. Typically, each instruction occupies 2-3 bytes, and the upper limit for each Method is 65536 bytes"
            )
        )
        val dynamicStrength by setting(
            name = enText("process.encrypt.number.number_basic_encrypt.dynamic_strength", "Dynamic strength"),
            value = true,
            desc = enText(
                "process.encrypt.number.number_basic_encrypt.dynamic_strength.desc",
                "When enabled, a modifier will be applied to all chances. Modifier = (MaxInsn - CurrentInsn) / MaxInsn"
            )
        )

        // Exclusion
        val exclusion by setting(
            enText("process.encrypt.number.number_basic_encrypt.method_exclusion", "Method exclusion"),
            listOf(
                "net/dummy/**", // Exclude package
                "net/dummy/Class", // Exclude class
                "net/dummy/Class.method", // Exclude method name
                "net/dummy/Class.method()V", // Exclude method with desc
            ),
            enText("process.encrypt.number.number_basic_encrypt.method_exclusion.desc", "Specify method exclusions."),
        )
    }

    private lateinit var methodExPredicate: NamePredicates

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            Logger.info(" - NumberBasicEncrypt: Encrypting numbers...")
            // TODO: there is a better way to do this instead of lateinit var
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        val shuffledListCache = localScopeValue { FastObjectArrayList<AbstractInsnNode>() }
        parForEachFiltered(buildFilterStrategy(config)) { classNode ->
            val counter = counter.local
            classNode.methods.asSequence()
                .filter { !it.isAbstract && !it.isNative }
                .forEach { method ->
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
                        if (config.integer && randomGen.nextFloat() < chanceModifier * config.integerChance) {
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
                        if (config.long && randomGen.nextFloat() < chanceModifier * config.longChance) {
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
                        if (config.float && randomGen.nextFloat() < chanceModifier * config.floatChance) {
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
                        if (config.double && randomGen.nextFloat() < chanceModifier * config.doubleChance) {
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
                +negative.toInsnNode()
                I2L
                +obfuscated.toInsnNode()
                I2L
                LXOR
                L2I
            } else {
                LDC(negative.toLong())
                L2I
                +obfuscated.toInsnNode()
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
        +obfuscated.toInsnNode()
        LXOR
    }

}
