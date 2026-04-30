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

    var index = -1; private set

    context(instance: Grunteon)
    val transformerSeed: String get() = instance.obfConfig.baseSeed() + name + index

    context(instance: Grunteon)
    internal fun buildStageImpl(pipelineBuilder: PipelineBuilder, config: TransformerConfig) {
        index = instance.transformers.indexOfFirst { it.first == this }
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