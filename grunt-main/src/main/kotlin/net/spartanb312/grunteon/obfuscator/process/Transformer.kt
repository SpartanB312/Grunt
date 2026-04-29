package net.spartanb312.grunteon.obfuscator.process

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.Languages
import net.spartanb312.grunteon.obfuscator.lang.MultiText
import net.spartanb312.grunteon.obfuscator.pipeline.OrderRule

abstract class Transformer<T : TransformerConfig>(
    val name: String,
    val category: Category
) {
    val engName = name
    val orderRules = mutableListOf<Pair<OrderRule, String>>()

    context(instance: Grunteon)
    val transformerSeed get() = instance.obfConfig.baseSeed() + name

    context(_: Grunteon)
    internal fun buildStageImpl(pipelineBuilder: PipelineBuilder, config: TransformerConfig) {
        context(pipelineBuilder) {
            @Suppress("UNCHECKED_CAST")
            buildStageImpl(config as T)
        }
    }

    context(instance: Grunteon, _: PipelineBuilder)
    protected abstract fun buildStageImpl(config: T)

    annotation class Description(
        val key: String,
        val enText: String,
    )
}