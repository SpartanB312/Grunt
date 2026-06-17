package net.spartanb312.grunt.yapyap

import net.spartanb312.grunt.yapyap.transformers.encrypt.number.NumberAttributeBasedEncrypt
import net.spartanb312.grunt.yapyap.transformers.encrypt.number.NumberSPECKEncrypt
import net.spartanb312.grunt.yapyap.transformers.encrypt.string.StringAttributeBasedEncrypt
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.plugin.PluginManager
import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerEntry
import net.spartanb312.grunteon.obfuscator.process.transformers.PostProcess
import net.spartanb312.grunteon.obfuscator.process.transformers.antidebug.AntiLLM
import net.spartanb312.grunteon.obfuscator.process.transformers.antidebug.RuntimeMaterial
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowFlattening
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowJump
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.ArithmeticSubstitute
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number.NumberBasicEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.string.StringArrayedEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.DeclaredFieldsExtract
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.ParameterObfuscate
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.TrashClassGenerator
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.*
import net.spartanb312.grunteon.obfuscator.process.transformers.other.*
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.FieldAccessProxy
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.InvokeDispatcher
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.InvokeProxy
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.*
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.logging.SimpleLogger
import java.util.*
import kotlin.io.path.Path
import kotlin.time.DurationUnit
import kotlin.time.measureTime

fun main(args: Array<String>) {
    if ("--silent" !in args) {
        Logger = SimpleLogger(
            "Grunteon", debug = { true }
        )
    }

    // TODO: Module scan
    // TODO: Module initialize

    PluginManager.loadPlugin(Yapyap)
    PluginManager.loadPlugins()

    val queue = ArrayDeque<Double>()
    repeat(args.getOrNull(0)?.toIntOrNull() ?: 1) {
        val config = ObfConfig(
            globalConfig = GlobalConfig(
//            output = null,
            ),
            transformers = listOf(
                // Optimize
                DeadCodeRemove.Config(),
                EnumOptimize.Config(),
                KotlinClassShrink.Config(),
                ClassShrink.Config(),
                SourceDebugInfoHide.Config(),
                StringEqualsOptimize.Config(),
                MethodInliner.Config(),
                // Misc
                TrashClassGenerator.Config(),
                // AntiLLM
                AntiLLM.Config(),
                // Encrypt
                ArithmeticSubstitute.Config(),
                NumberBasicEncrypt.Config(),
                StringArrayedEncrypt.Config(),
                NumberAttributeBasedEncrypt.Config(),
                NumberSPECKEncrypt.Config(),
                StringAttributeBasedEncrypt.Config(),
                // Misc
                DeclaredFieldsExtract.Config(),
                ParameterObfuscate.Config(),
                // Controlflow
                ControlflowFlattening.Config(),
                ControlflowJump.Config(),
                // SSARoundTrip.Config(),
                // Redirect
                InvokeDispatcher.Config(),
                InvokeProxy.Config(),
                FieldAccessProxy.Config(),
                // Anti debug
                RuntimeMaterial.Config(),
                // Other
                DecompilerCrasher.Config(),
                ShuffleMembers.Config(),
                Watermark.Config(),
                // Renamer
                LocalVarRenamer.Config(),
                ClassRenamer.Config(),
                FieldRenamer.Config(),
                MethodRenamer.Config(),
                MixinClassRenamer.Config(),
                MixinFieldRenamer.Config(),
                // Other
                FakeSyntheticBridge.Config(),
                ReferenceObfuscate.Config(),
                // Post
                PostProcess.Config()
            ).map { config -> TransformerEntry(config = config) }
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
