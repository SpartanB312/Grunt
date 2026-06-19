package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.pipeline.CreditsCalc
import net.spartanb312.grunteon.obfuscator.pipeline.CreditsSummary
import net.spartanb312.grunteon.obfuscator.pipeline.FinalOutput
import net.spartanb312.grunteon.obfuscator.pipeline.JvmObfuscation
import net.spartanb312.grunteon.obfuscator.pipeline.NativeObfuscation
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.nativecode.NativePipelineConfig
import net.spartanb312.grunteon.obfuscator.process.nativecode.NativePipelineRunner
import net.spartanb312.grunteon.obfuscator.process.resource.JarDumper
import net.spartanb312.grunteon.obfuscator.process.resource.ObfuscationIO
import net.spartanb312.grunteon.obfuscator.process.resource.WorkResources
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.mapping.MappingApplier
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.mapping.MappingSource
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.mapping.NameMapping
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.filters.ClassPredicate
import net.spartanb312.grunteon.obfuscator.util.filters.buildClassNamePredicates
import net.spartanb312.grunteon.obfuscator.util.numerical.formatInteger
import java.util.*
import kotlin.math.roundToLong

// Grunteon process instance
class Grunteon(
    val globalConfig: GlobalConfig,
    val io: ObfuscationIO,
    val workRes: WorkResources,
    val transformers: List<Pair<Transformer<*>, TransformerConfig>>,
    val nativePipelineConfig: NativePipelineConfig = NativePipelineConfig(),
) {
    /**
     * Resources
     */
    val nameMapping = NameMapping()
    var creditsSummary: CreditsSummary = CreditsSummary.EMPTY

    fun run() {
        // TODO: Stage pipeline
        // JVM obfuscate stage
        JvmObfuscation().execute(this)
        // Native obfuscate stage
        NativeObfuscation().execute(this)
        // Output stage
        FinalOutput().execute(this)
    }

    val mixinInclusion = ClassPredicate.IncludeExclude(
        includeStrategy = buildClassNamePredicates(globalConfig.mixinExclusions)
    )
    val mixinExclusion = ClassPredicate.IncludeExclude(
        includeStrategy = buildClassNamePredicates(listOf("**")),
        excludeStrategy = buildClassNamePredicates(globalConfig.mixinExclusions)
    )
    val globalExclusion = ClassPredicate.IncludeExclude(
        includeStrategy = buildClassNamePredicates(listOf("**")),
        excludeStrategy = buildClassNamePredicates(globalConfig.exclusions)
    )

    companion object {
        fun create(config: ObfConfig): Grunteon {
            return create(config, ObfuscationIO.fromConfig(config.globalConfig))
        }

        fun create(config: ObfConfig, io: ObfuscationIO): Grunteon {
            Logger.info("Executing obfuscating job...")

            val workRes = WorkResources.read(io.input, io.libraries)

            val transformerAndConfig = config.transformers
                .asSequence()
                .filter { it.enabled }
                .map { it.config }
                .mapTo(mutableListOf()) { transformerConfig ->
                    val entry = TransformerRegistry.find(transformerConfig)
                        ?: throw IllegalArgumentException("Unregistered transformer config: ${transformerConfig::class.qualifiedName}")
                    entry.createTransformer() to transformerConfig
                }

            val transformerList = transformerAndConfig.map { it.first }

            var lastRenamerIndex = -1
            val errors = mutableListOf<String>()
            transformerAndConfig.forEachIndexed { index, (transformer, _) ->
                if (transformer is MappingSource) lastRenamerIndex = index
                transformer.orderRules.forEach {
                    if (!it.first.invoke(transformerList, index)) errors.add("${transformer.engName}: ${it.second}")
                }
            }
            check(errors.isEmpty()) {
                "Transformer order rules violated:\n" + errors.joinToString("\n")
            }
            if (lastRenamerIndex != -1) {
                transformerAndConfig.add(lastRenamerIndex + 1, MappingApplier() to MappingApplier.Config())
            }

            return Grunteon(
                globalConfig = config.globalConfig,
                io = io,
                workRes = workRes,
                transformers = transformerAndConfig,
                nativePipelineConfig = config.nativePipeline
            )
        }
    }
}
