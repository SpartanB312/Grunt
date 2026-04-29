package net.spartanb312.grunteon.obfuscator.process.transformers.encrypt

import kotlinx.serialization.Serializable
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.DISABLE_ARITHMETIC_SUBSTITUTE
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.methodFullDesc
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.isExcluded
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import net.spartanb312.grunteon.obfuscator.util.numerical.replaceIAND
import net.spartanb312.grunteon.obfuscator.util.numerical.replaceINEG
import net.spartanb312.grunteon.obfuscator.util.numerical.replaceIOR
import net.spartanb312.grunteon.obfuscator.util.numerical.replaceIXOR
import org.objectweb.asm.Opcodes

@Transformer.Description(
    "process.encrypt.arithmetic_substitute.desc",
    "Replace arithmetic ops to substitutions"
)
class ArithmeticSubstitute : Transformer<ArithmeticSubstitute.Config>(
    "ArithmeticSubstitute",
    Category.Encryption,
) {
    @Serializable
    data class Config(
        @SettingDesc(enText = "Specify class include/exclude rules")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc(enText = "Ops replace rate. Range: 0.0..1.0")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        val chance: Double = 0.3,
        @SettingDesc(enText = "The upper limit of instruction count for a Method. Typically, each instruction occupies 2-3 bytes, and the upper limit for each Method is 65536 bytes")
        val maxInstructions: Int = 16384,
        @SettingDesc(enText = "When enabled, a modifier will be applied to all chances. Modifier = (MaxInsn - CurrentInsn) / MaxInsn")
        val dynamicStrength: Boolean = true,
        @SettingDesc(enText = "Specify method exclusions.")
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
            //Logger.info(" > ArithmeticSubstitute: Replacing arithmetic instructions...")
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            val counter = counter.local
            if (classNode.isExcluded(DISABLE_ARITHMETIC_SUBSTITUTE)) return@parForEachClassesFiltered
            classNode.methods.asSequence()
                .filter { !it.isAbstract && !it.isNative }
                .forEach { method ->
                    if (method.isExcluded(DISABLE_ARITHMETIC_SUBSTITUTE)) return@forEach
                    val excluded = methodExPredicate.matchedAnyBy(methodFullDesc(classNode, method))
                    if (excluded) return@forEach
                    if ((method.instructions?.size() ?: 0) >= config.maxInstructions) return@forEach
                    val chanceModifier =
                        (if (config.dynamicStrength) (config.maxInstructions - method.instructions.size()).toFloat() / config.maxInstructions
                        else 1f).coerceIn(0f, 1f)

                    val randomGen = Xoshiro256PPRandom(getSeed(classNode.name, method.name, method.desc))

                    var modified = false
                    val insnList = instructions {
                        var skipInsn = 0
                        for ((index, insn) in method.instructions.withIndex()) {
                            if (skipInsn > 0) {
                                skipInsn--
                                continue
                            }
                            // Avoid "method too large"
                            val currentSize = insnList.size() + method.instructions.size() - index
                            if (currentSize >= config.maxInstructions || randomGen.nextFloat() >= chanceModifier * config.chance) {
                                +insn
                                continue
                            }
                            if (index < method.instructions.size() - 2) {
                                val next = method.instructions[index + 1]
                                val nextNext = method.instructions[index + 2]
                                when {
                                    insn.opcode == Opcodes.ICONST_M1 && next.opcode == Opcodes.IXOR && nextNext.opcode == Opcodes.IADD -> {
                                        if (randomGen.nextBoolean()) {
                                            DUP_X1
                                            IOR
                                            SWAP
                                            ISUB
                                        } else {
                                            SWAP
                                            DUP_X1
                                            IAND
                                            ISUB
                                        }
                                        skipInsn += 2
                                        modified = true
                                    }

                                    insn.opcode == Opcodes.ISUB && next.opcode == Opcodes.ICONST_M1 && nextNext.opcode == Opcodes.IXOR -> {
                                        if (randomGen.nextBoolean()) {
                                            SWAP
                                            ISUB
                                            ICONST_1
                                            ISUB
                                        } else {
                                            SWAP
                                            ICONST_M1
                                            IXOR
                                            IADD
                                        }
                                        skipInsn += 2
                                        modified = true
                                    }

                                    insn.opcode == Opcodes.ICONST_M1 && next.opcode == Opcodes.IXOR -> {
                                        INEG
                                        ICONST_M1
                                        IADD
                                        skipInsn += 1
                                        modified = true
                                    }

                                    insn.opcode == Opcodes.INEG -> {
                                        +replaceINEG()
                                        modified = true
                                    }

                                    insn.opcode == Opcodes.IXOR -> {
                                        +replaceIXOR()
                                        modified = true
                                    }

                                    insn.opcode == Opcodes.IOR -> {
                                        +replaceIOR()
                                        modified = true
                                    }

                                    insn.opcode == Opcodes.IAND -> {
                                        +replaceIAND()
                                        modified = true
                                    }

                                    else -> {
                                        counter.add(-1)
                                        +insn
                                    }
                                }
                                counter.add(1)
                            } else +insn
                        }
                    }
                    if (modified) method.instructions = insnList
                }
        }
        post {
            Logger.info(" - ArithmeticSubstitute:")
            Logger.info("    Replaced ${counter.global.get()} instructions")
        }
    }

}
