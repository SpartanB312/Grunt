package net.spartanb312.grunteon.obfuscator.pipeline

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.PipelineBuilder
import net.spartanb312.grunteon.obfuscator.process.WorkerContext
import net.spartanb312.grunteon.obfuscator.process.nativecode.NativePipelineRunner
import net.spartanb312.grunteon.obfuscator.process.resource.JarDumper
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.numerical.formatInteger
import java.util.Locale
import kotlin.math.roundToLong

class JvmObfuscation : ExecutionStage("JvmObfuscation") {
    override fun Grunteon.execute() {
        context(workRes) {
            Logger.info("Obfuscating...")
            val pipelineBuilder = PipelineBuilder()
            transformers.forEach { (transformer, config) ->
                transformer.buildStageImpl(pipelineBuilder, config)
            }
            val workerContext = WorkerContext()
            workerContext.execute(this, pipelineBuilder)
            creditsSummary = CreditsCalc.summarize(transformers.map { it.first })
            val totalCredits = creditsSummary.totalCredits
            Logger.debug("Credits used: ${formatInteger(totalCredits.roundToLong())}")
            creditsSummary.transformers.forEach {
                val rate = it.credits / totalCredits * 100
                Logger.debug(
                    "    ${it.name}[${String.format(Locale.US, "%.2f", rate)}%]:" +
                        " credits=${formatInteger(it.credits.roundToLong())}, " +
                        "raw=${formatInteger(it.raw)}, multiplier=${it.baseMultiplier}"
                )
            }
        }
    }
}

class NativeObfuscation : ExecutionStage("NativeObfuscation") {
    override fun Grunteon.execute() {
        val nativeConfig = nativePipelineConfig
        NativePipelineRunner.run(nativeConfig)
    }
}

class FinalOutput : ExecutionStage("FinalOutput") {
    override fun Grunteon.execute() {
        io.output?.let { JarDumper.dumpJar(it) }
        if (globalConfig.dumpMappings) io.mappingsOutput?.let { nameMapping.dump(it) }
    }
}