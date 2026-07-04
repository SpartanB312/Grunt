package net.spartanb312.grunteon.obfuscator.process.transformers.other

import kotlinx.serialization.Serializable
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.field
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.collection.random
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.isInterface

@Transformer.CreditMultiplier(0.5)
@Transformer.Stability(StableLevel.RockSolid)
@Transformer.Description(
    "process.other.watermark.desc",
    "Add watermarks"
)
class Watermark : Transformer<Watermark.Config>(
    "Watermark",
    Category.Other,
) {
    @Serializable
    data class Config(
        @SettingName("Class filter")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Watermark member names")
        @SettingName("Names")
        val names: List<String> = listOf("Grunt", "Gruntpocalypse", "Grunteon"),
        @SettingDesc("Watermark messages")
        @SettingName("Messages")
        val messages: List<String> = listOf(
            "PROTECTED BY GRUNTEON",
            "PROTECTED BY EVERETT",
            "PROTECTED BY YuShengJun"
        ),
        @SettingDesc("Add field watermark")
        @SettingName("Field mark")
        val fieldMark: Boolean = true,
        @SettingDesc("Add method watermark")
        @SettingName("Method mark")
        val methodMark: Boolean = true
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            //Logger.info(" > Watermark: Adding watermarks...")
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        val randomNums = listOf(114514, 1919810, 69420, 911, 1186, 1453, 2026)
        parForEachClassesFiltered(
            instance.globalExclusion
                .and(instance.mixinExclusion)
                .and(config.classFilter.toClassPredicate())
        ) { classNode ->
            if (classNode.isInterface) return@parForEachClassesFiltered
            val counter = counter.local
            val randomGen = Xoshiro256PPRandom(getSeed(classNode.name))
            if (config.fieldMark) {
                classNode.fields = classNode.fields ?: arrayListOf()
                val marker = config.messages.random(randomGen)
                when (randomGen.nextInt(0, 3)) {
                    0 -> classNode.fields.add(
                        field(
                            PRIVATE + STATIC,
                            config.names.random(randomGen),
                            "Ljava/lang/String;",
                            null,
                            marker
                        )
                    )
                    1 -> classNode.fields.add(
                        field(
                            PRIVATE + STATIC,
                            "_$marker _",
                            "I",
                            null,
                            randomNums.random(randomGen)
                        )
                    )
                    3 -> classNode.fields.add(
                        field(
                            PRIVATE + STATIC,
                            config.names.random(randomGen),
                            "Ljava/lang/String;",
                            null,
                            marker
                        )
                    )
                }
                counter.add()
            }
            if (config.methodMark) {
                classNode.methods = classNode.methods ?: arrayListOf()
                val marker = config.messages.random(randomGen)
                when (randomGen.nextInt(0, 3)) {
                    0 -> classNode.methods.add(
                        method(
                            PRIVATE + STATIC,
                            config.names.random(randomGen),
                            "()Ljava/lang/String;"
                        ) {
                            INSTRUCTIONS {
                                LDC(marker)
                                ARETURN
                            }
                        }
                    )
                    1 -> classNode.methods.add(
                        method(
                            PRIVATE + STATIC,
                            config.names.random(randomGen),
                            "()Ljava/lang/String;"
                        ) {
                            INSTRUCTIONS {
                                LDC(marker)
                                ARETURN
                            }
                        }
                    )
                    2 -> classNode.methods.add(
                        method(
                            PRIVATE + STATIC,
                            config.names.random(randomGen),
                            "()Ljava/lang/String;"
                        ) {
                            INSTRUCTIONS {
                                LDC(marker)
                                ARETURN
                            }
                        }
                    )
                }
                counter.add()
            }
        }
        post {
            Logger.info(" - Watermark:")
            credit.add(counter.global.get() * 50L)
            Logger.info("    Added ${counter.global.get()} watermarks")
        }
    }

}