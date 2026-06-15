package net.spartanb312.grunt.yapyap.transformers.encrypt.number

import kotlinx.serialization.Serializable
import net.spartanb312.genesis.kotlin.InsnListBuilder
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.*
import net.spartanb312.grunteon.obfuscator.util.collection.FastObjectArrayList
import net.spartanb312.grunteon.obfuscator.util.collection.shuffle
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.*
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.isExcluded
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import net.spartanb312.grunteon.obfuscator.util.numerical.asInt
import net.spartanb312.grunteon.obfuscator.util.numerical.asLong
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

@Transformer.Description(
    "process.encrypt.number.number_speck_encrypt.desc",
    "Encrypt numbers using SPECK block ciphers"
)
class NumberSPECKEncrypt : Transformer<NumberSPECKEncrypt.Config>(
    "NumberSPECKEncrypt",
    Category.Encryption,
) {

    @Serializable
    data class Config(
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Encrypt integers with SPECK32/64")
        @SettingName("Integer")
        val integer: Boolean = true,
        @SettingDesc("Encrypt longs with SPECK64/128")
        @SettingName("Long")
        val long: Boolean = true,
        @SettingDesc("Encrypt floats through SPECK32/64 bit encryption")
        @SettingName("Float")
        val float: Boolean = true,
        @SettingDesc("Encrypt doubles through SPECK64/128 bit encryption")
        @SettingName("Double")
        val double: Boolean = true,
        @SettingDesc("Number encrypt rate.")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Chance")
        val chance: Decimal = 1.0.toDecimal(),
        @SettingDesc("The upper limit of instruction count for a Method")
        @SettingName("Max instructions")
        val maxInstructions: Int = 16384,
        @SettingDesc("When enabled, a modifier will be applied to chance. Modifier = (MaxInsn - CurrentInsn) / MaxInsn")
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

    private lateinit var methodExPredicate: NamePredicates

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }

        val counter = reducibleScopeValue { MergeableCounter() }
        val generatedCompanions = reducibleScopeValue {
            MergeableObjectList<ClassNode>(FastObjectArrayList())
        }
        val shuffledListCache = localScopeValue { FastObjectArrayList<AbstractInsnNode>() }

        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            val counter = counter.local
            if (classNode.isExcluded(DISABLE_NUMBER_ENCRYPT)) return@parForEachClassesFiltered
            if (classNode.version < Opcodes.V1_5) return@parForEachClassesFiltered

            val classRandom = Xoshiro256PPRandom(getSeed(classNode.name, "number-speck"))
            val companionName = "${classNode.name}\$NumberSPECK_${classRandom.getRandomString(8)}"
            var companion: ClassNode? = null
            var generatedMethodId = 0

            fun companion(): ClassNode {
                val current = companion
                if (current != null) return current
                return ClassNode().apply {
                    visit(classNode.version, Opcodes.ACC_PUBLIC, companionName, null, "java/lang/Object", null)
                    appendAnnotation(GENERATED_CLASS)
                    appendAnnotation(DISABLE_NUMBER_ENCRYPT)
                    companion = this
                }
            }

            classNode.methods.asSequence()
                .filter { !it.isAbstract && !it.isNative }
                .forEach { method ->
                    if (method.isExcluded(DISABLE_NUMBER_ENCRYPT)) return@forEach
                    if (methodExPredicate.matchedAnyBy(methodFullDesc(classNode, method))) return@forEach
                    if ((method.instructions?.size() ?: 0) >= config.maxInstructions) return@forEach

                    val chanceModifier =
                        (if (config.dynamicStrength) {
                            (config.maxInstructions - method.instructions.size()).toFloat() / config.maxInstructions
                        } else 1f).coerceIn(0f, 1f)
                    val randomGen = Xoshiro256PPRandom(getSeed(classNode.name, method.name, method.desc, "speck"))
                    val shuffledList = shuffledListCache.local
                    shuffledList.clearFast()

                    method.instructions.filterTo(shuffledList) { it.opcode != Opcodes.NEWARRAY }
                    shuffledList.shuffle(randomGen)
                    shuffledList.forEach { instruction ->
                        if (randomGen.nextFloat() >= chanceModifier * config.chance.toFloat()) return@forEach
                        val generatedMethod = createDecryptMethod(config, randomGen, instruction) {
                            "m${generatedMethodId++}"
                        } ?: return@forEach

                        companion().methods.add(generatedMethod)
                        method.instructions.insertBefore(
                            instruction,
                            instructions {
                                INVOKESTATIC(companionName, generatedMethod.name, generatedMethod.desc)
                            }
                        )
                        method.instructions.remove(instruction)
                        counter.add()
                    }
                }

            companion?.takeIf { it.methods.isNotEmpty() }?.let {
                generatedCompanions.local.add(it)
            }
        }

        seq {
            generatedCompanions.global.forEach {
                instance.workRes.addGeneratedClass(it)
            }
        }

        post {
            Logger.info(" - NumberSPECKEncrypt:")
            Logger.info("    Encrypted ${counter.global.get()} numbers")
            Logger.info("    Generated ${generatedCompanions.global.size} SPECK helper classes")
        }
    }

    private fun createDecryptMethod(
        config: Config,
        random: UniformRandomProvider,
        instruction: AbstractInsnNode,
        nextName: () -> String
    ): MethodNode? {
        if (config.integer) {
            instruction.getIntValue()?.let {
                return createIntDecryptMethod(random, nextName(), it)
            }
        }
        if (config.long) {
            instruction.getLongValue()?.let {
                return createLongDecryptMethod(random, nextName(), it)
            }
        }
        if (config.float) {
            instruction.getFloatValue()?.let {
                return createFloatDecryptMethod(random, nextName(), it)
            }
        }
        if (config.double) {
            instruction.getDoubleValue()?.let {
                return createDoubleDecryptMethod(random, nextName(), it)
            }
        }
        return null
    }

    private fun createIntDecryptMethod(
        random: UniformRandomProvider,
        name: String,
        value: Int
    ): MethodNode {
        val key = random.nextSpeck32Key()
        val roundKeys = speck32RoundKeys(key)
        val encrypted = speck32Encrypt(value, roundKeys)
        return method(PUBLIC + STATIC, name, "()I") {
            INSTRUCTIONS {
                INT((encrypted ushr 16) and SPECK32_MASK)
                ISTORE(0)
                INT(encrypted and SPECK32_MASK)
                ISTORE(1)
                speck32Decrypt(roundKeys)
                ILOAD(0)
                INT(16)
                ISHL
                ILOAD(1)
                IOR
                IRETURN
            }
        }.appendAnnotation(GENERATED_METHOD)
    }

    private fun createLongDecryptMethod(
        random: UniformRandomProvider,
        name: String,
        value: Long
    ): MethodNode {
        val key = random.nextSpeck64Key()
        val roundKeys = speck64RoundKeys(key)
        val encrypted = speck64Encrypt(value, roundKeys)
        return method(PUBLIC + STATIC, name, "()J") {
            INSTRUCTIONS {
                INT((encrypted ushr 32).toInt())
                ISTORE(0)
                INT(encrypted.toInt())
                ISTORE(1)
                speck64Decrypt(roundKeys)
                ILOAD(0)
                I2L
                INT(32)
                LSHL
                ILOAD(1)
                I2L
                LDC(0xffffffffL)
                LAND
                LOR
                LRETURN
            }
        }.appendAnnotation(GENERATED_METHOD)
    }

    private fun createFloatDecryptMethod(
        random: UniformRandomProvider,
        name: String,
        value: Float
    ): MethodNode {
        val key = random.nextSpeck32Key()
        val roundKeys = speck32RoundKeys(key)
        val encrypted = speck32Encrypt(value.asInt(), roundKeys)
        return method(PUBLIC + STATIC, name, "()F") {
            INSTRUCTIONS {
                INT((encrypted ushr 16) and SPECK32_MASK)
                ISTORE(0)
                INT(encrypted and SPECK32_MASK)
                ISTORE(1)
                speck32Decrypt(roundKeys)
                ILOAD(0)
                INT(16)
                ISHL
                ILOAD(1)
                IOR
                INVOKESTATIC("java/lang/Float", "intBitsToFloat", "(I)F")
                FRETURN
            }
        }.appendAnnotation(GENERATED_METHOD)
    }

    private fun createDoubleDecryptMethod(
        random: UniformRandomProvider,
        name: String,
        value: Double
    ): MethodNode {
        val key = random.nextSpeck64Key()
        val roundKeys = speck64RoundKeys(key)
        val encrypted = speck64Encrypt(value.asLong(), roundKeys)
        return method(PUBLIC + STATIC, name, "()D") {
            INSTRUCTIONS {
                INT((encrypted ushr 32).toInt())
                ISTORE(0)
                INT(encrypted.toInt())
                ISTORE(1)
                speck64Decrypt(roundKeys)
                ILOAD(0)
                I2L
                INT(32)
                LSHL
                ILOAD(1)
                I2L
                LDC(0xffffffffL)
                LAND
                LOR
                INVOKESTATIC("java/lang/Double", "longBitsToDouble", "(J)D")
                DRETURN
            }
        }.appendAnnotation(GENERATED_METHOD)
    }

    private fun InsnListBuilder.speck32Decrypt(roundKeys: IntArray) {
        for (i in roundKeys.indices.reversed()) {
            val key = roundKeys[i]
            ILOAD(1)
            ILOAD(0)
            IXOR
            ISTORE(1)
            ILOAD(1)
            INT(SPECK32_BETA)
            IUSHR
            ILOAD(1)
            INT(SPECK32_WORD_BITS - SPECK32_BETA)
            ISHL
            IOR
            INT(SPECK32_MASK)
            IAND
            ISTORE(1)
            ILOAD(0)
            INT(key)
            IXOR
            ILOAD(1)
            ISUB
            INT(SPECK32_MASK)
            IAND
            ISTORE(0)
            ILOAD(0)
            INT(SPECK32_ALPHA)
            ISHL
            ILOAD(0)
            INT(SPECK32_WORD_BITS - SPECK32_ALPHA)
            IUSHR
            IOR
            INT(SPECK32_MASK)
            IAND
            ISTORE(0)
        }
    }

    private fun InsnListBuilder.speck64Decrypt(roundKeys: IntArray) {
        for (i in roundKeys.indices.reversed()) {
            val key = roundKeys[i]
            ILOAD(1)
            ILOAD(0)
            IXOR
            INT(SPECK64_BETA)
            INVOKESTATIC("java/lang/Integer", "rotateRight", "(II)I")
            ISTORE(1)
            ILOAD(0)
            INT(key)
            IXOR
            ILOAD(1)
            ISUB
            INT(SPECK64_ALPHA)
            INVOKESTATIC("java/lang/Integer", "rotateLeft", "(II)I")
            ISTORE(0)
        }
    }

    private fun UniformRandomProvider.nextSpeck32Key(): IntArray =
        IntArray(SPECK32_KEY_WORDS) { nextInt() and SPECK32_MASK }

    private fun UniformRandomProvider.nextSpeck64Key(): IntArray =
        IntArray(SPECK64_KEY_WORDS) { nextInt() }

    private fun speck32RoundKeys(key: IntArray): IntArray {
        val l = IntArray(SPECK32_ROUNDS + SPECK32_KEY_WORDS - 1)
        val roundKeys = IntArray(SPECK32_ROUNDS)
        l[0] = key[1] and SPECK32_MASK
        l[1] = key[2] and SPECK32_MASK
        l[2] = key[3] and SPECK32_MASK
        roundKeys[0] = key[0] and SPECK32_MASK
        for (i in 0 until SPECK32_ROUNDS - 1) {
            l[i + SPECK32_KEY_WORDS - 1] =
                (((ror16(l[i], SPECK32_ALPHA) + roundKeys[i]) and SPECK32_MASK) xor i) and SPECK32_MASK
            roundKeys[i + 1] = (rol16(roundKeys[i], SPECK32_BETA) xor l[i + SPECK32_KEY_WORDS - 1]) and SPECK32_MASK
        }
        return roundKeys
    }

    private fun speck64RoundKeys(key: IntArray): IntArray {
        val l = IntArray(SPECK64_ROUNDS + SPECK64_KEY_WORDS - 1)
        val roundKeys = IntArray(SPECK64_ROUNDS)
        l[0] = key[1]
        l[1] = key[2]
        l[2] = key[3]
        roundKeys[0] = key[0]
        for (i in 0 until SPECK64_ROUNDS - 1) {
            l[i + SPECK64_KEY_WORDS - 1] = Integer.rotateRight(l[i], SPECK64_ALPHA) + roundKeys[i] xor i
            roundKeys[i + 1] = Integer.rotateLeft(roundKeys[i], SPECK64_BETA) xor l[i + SPECK64_KEY_WORDS - 1]
        }
        return roundKeys
    }

    private fun speck32Encrypt(value: Int, roundKeys: IntArray): Int {
        var x = (value ushr 16) and SPECK32_MASK
        var y = value and SPECK32_MASK
        roundKeys.forEach { key ->
            x = (((ror16(x, SPECK32_ALPHA) + y) and SPECK32_MASK) xor key) and SPECK32_MASK
            y = (rol16(y, SPECK32_BETA) xor x) and SPECK32_MASK
        }
        return (x shl 16) or y
    }

    private fun speck64Encrypt(value: Long, roundKeys: IntArray): Long {
        var x = (value ushr 32).toInt()
        var y = value.toInt()
        roundKeys.forEach { key ->
            x = Integer.rotateRight(x, SPECK64_ALPHA) + y xor key
            y = Integer.rotateLeft(y, SPECK64_BETA) xor x
        }
        return (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
    }

    private fun rol16(value: Int, distance: Int): Int =
        ((value shl distance) or (value ushr (SPECK32_WORD_BITS - distance))) and SPECK32_MASK

    private fun ror16(value: Int, distance: Int): Int =
        ((value ushr distance) or (value shl (SPECK32_WORD_BITS - distance))) and SPECK32_MASK

    private companion object {
        private const val SPECK32_MASK = 0xffff
        private const val SPECK32_WORD_BITS = 16
        private const val SPECK32_KEY_WORDS = 4
        private const val SPECK32_ROUNDS = 22
        private const val SPECK32_ALPHA = 7
        private const val SPECK32_BETA = 2
        private const val SPECK64_KEY_WORDS = 4
        private const val SPECK64_ROUNDS = 27
        private const val SPECK64_ALPHA = 8
        private const val SPECK64_BETA = 3
    }

}
