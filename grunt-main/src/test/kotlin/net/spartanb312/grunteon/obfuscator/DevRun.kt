package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.transformers.PostProcess
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.ArithmeticSubstitute
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number.NumberBasicEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.string.StringArrayedEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.DeclaredFieldsExtract
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.ParameterObfuscate
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.*
import net.spartanb312.grunteon.obfuscator.process.transformers.other.DecompilerCrasher
import net.spartanb312.grunteon.obfuscator.process.transformers.other.FakeSyntheticBridge
import net.spartanb312.grunteon.obfuscator.process.transformers.other.ShuffleMembers
import net.spartanb312.grunteon.obfuscator.process.transformers.other.Watermark
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.FieldAccessProxy
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.InvokeDispatcher
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.InvokeProxy
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.ClassRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.FieldRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.LocalVarRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.MethodRenamer
import net.spartanb312.grunteon.obfuscator.plugin.PluginManager
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.logging.SimpleLogger
import java.util.*
import kotlin.io.path.Path
import kotlin.time.DurationUnit
import kotlin.time.measureTime

fun main(args: Array<String>) {
    if ("--silent" !in args) {
        Logger = SimpleLogger(
            "Grunteon"
        )
    }

    // TODO: Module scan
    // TODO: Module initialize

    PluginManager.loadPlugins()

    val queue = ArrayDeque<Double>()
    repeat(args.getOrNull(0)?.toIntOrNull() ?: 1) {
        val config = ObfConfig(
            transformerConfigs = listOf(
                // Optimize
                DeadCodeRemove.Config(),
                EnumOptimize.Config(),
                KotlinClassShrink.Config(),
                ClassShrink.Config(),
                SourceDebugInfoHide.Config(),
                StringEqualsOptimize.Config(),
                // Encrypt
                ArithmeticSubstitute.Config(),
                NumberBasicEncrypt.Config(),
                StringArrayedEncrypt.Config(),
                // Misc
                DeclaredFieldsExtract.Config(),
                ParameterObfuscate.Config(),
                // Controlflow
                // Redirect
                InvokeDispatcher.Config(),
                InvokeProxy.Config(),
                FieldAccessProxy.Config(),
                // Renamer
                LocalVarRenamer.Config(),
                ClassRenamer.Config(),
                FieldRenamer.Config(),
                MethodRenamer.Config(),
                // Other
                FakeSyntheticBridge.Config(),
                DecompilerCrasher.Config(),
                ShuffleMembers.Config(),
                Watermark.Config(),
                // Post
                PostProcess.Config()
            )
        )
        ObfConfig.write(config, Path("config.json"))
        val instance = Grunteon.create(config)

        measureTime {
            instance.execute()
        }.toDouble(DurationUnit.MILLISECONDS).also { time ->
            while (queue.size >= 5) queue.poll()
            queue.add(time)
            println("Execution time: ${"%.2f".format(time)} ms (average: ${"%.2f".format(queue.average())} ms)")
        }
    }
}
