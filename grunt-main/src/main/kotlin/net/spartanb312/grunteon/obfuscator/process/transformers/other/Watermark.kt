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
import net.spartanb312.grunteon.obfuscator.util.extensions.isInterface

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
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc(enText = "Watermark member names")
        val names: List<String> = listOf("Grunt", "Gruntpocalypse", "Grunteon"),
        @SettingDesc(enText = "Watermark messages")
        val messages: List<String> = listOf(
            "PROTECTED BY GRUNTEON",
            "PROTECTED BY EVERETT",
            "PROTECTED BY YuShengJun"
        ),
        @SettingDesc(enText = "Add field watermark")
        val fieldMark: Boolean = true,
        @SettingDesc(enText = "Add method watermark")
        val methodMark: Boolean = true
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            //Logger.info(" > Watermark: Adding watermarks...")
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            if (classNode.isInterface) return@parForEachClassesFiltered
            val counter = counter.local
            if (config.fieldMark) {
                classNode.fields = classNode.fields ?: arrayListOf()
                val marker = config.messages.random()
                when ((0..2).random()) {
                    0 -> classNode.fields.add(
                        field(
                            PRIVATE + STATIC,
                            config.names.random(),
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
                            listOf(114514, 1919810, 69420, 911, 8964).random()
                        )
                    )

                    2 -> classNode.fields.add(
                        field(
                            PRIVATE + STATIC,
                            config.names.random(),
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
                val marker = config.messages.random()
                when ((0..2).random()) {
                    0 -> classNode.methods.add(
                        method(
                            PRIVATE + STATIC,
                            config.names.random(),
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
                            config.names.random(),
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
                            config.names.random(),
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
            Logger.info("    Added ${counter.global.get()} watermarks")
        }
    }

}