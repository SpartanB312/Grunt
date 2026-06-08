package net.spartanb312.grunteon.obfuscator.process

import net.spartanb312.grunteon.obfuscator.process.transformers.PostProcess
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowFlattening
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowFlatteningSSA
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowJump
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.FlowIRRoundTrip
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.SSARoundTrip
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.ArithmeticSubstitute
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number.NumberBasicEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.string.StringArrayedEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.DeclaredFieldsExtract
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.ParameterObfuscate
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.ClassShrink
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.DeadCodeRemove
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.EnumOptimize
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.KotlinClassShrink
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.MethodInliner
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.SourceDebugInfoHide
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.StringEqualsOptimize
import net.spartanb312.grunteon.obfuscator.process.transformers.other.DecompilerCrasher
import net.spartanb312.grunteon.obfuscator.process.transformers.other.FakeSyntheticBridge
import net.spartanb312.grunteon.obfuscator.process.transformers.other.ReferenceObfuscate
import net.spartanb312.grunteon.obfuscator.process.transformers.other.ShuffleMembers
import net.spartanb312.grunteon.obfuscator.process.transformers.other.Watermark
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.FieldAccessProxy
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.InvokeDispatcher
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.InvokeProxy
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.ClassRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.FieldRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.LocalVarRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.MethodRenamer
import kotlin.reflect.KClass

data class TransformerRegistryEntry(
    val configClass: KClass<out TransformerConfig>,
    val createTransformer: () -> Transformer<*>,
    val createConfig: () -> TransformerConfig,
    val owner: String = "grunteon",
) {
    val transformerPrototype: Transformer<*> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        createTransformer()
    }
}

object TransformerRegistry {
    private val builtinEntries: List<TransformerRegistryEntry> = listOf(
        entry({ DeadCodeRemove() }, { DeadCodeRemove.Config() }),
        entry({ EnumOptimize() }, { EnumOptimize.Config() }),
        entry({ KotlinClassShrink() }, { KotlinClassShrink.Config() }),
        entry({ ClassShrink() }, { ClassShrink.Config() }),
        entry({ SourceDebugInfoHide() }, { SourceDebugInfoHide.Config() }),
        entry({ StringEqualsOptimize() }, { StringEqualsOptimize.Config() }),
        entry({ MethodInliner() }, { MethodInliner.Config() }),
        entry({ ControlflowFlattening() }, { ControlflowFlattening.Config() }),
        entry({ ControlflowFlatteningSSA() }, { ControlflowFlatteningSSA.Config() }),
        entry({ ControlflowJump() }, { ControlflowJump.Config() }),
        entry({ FlowIRRoundTrip() }, { FlowIRRoundTrip.Config() }),
        entry({ SSARoundTrip() }, { SSARoundTrip.Config() }),
        entry({ ArithmeticSubstitute() }, { ArithmeticSubstitute.Config() }),
        entry({ NumberBasicEncrypt() }, { NumberBasicEncrypt.Config() }),
        entry({ StringArrayedEncrypt() }, { StringArrayedEncrypt.Config() }),
        entry({ DeclaredFieldsExtract() }, { DeclaredFieldsExtract.Config() }),
        entry({ ParameterObfuscate() }, { ParameterObfuscate.Config() }),
        entry({ InvokeDispatcher() }, { InvokeDispatcher.Config() }),
        entry({ InvokeProxy() }, { InvokeProxy.Config() }),
        entry({ FieldAccessProxy() }, { FieldAccessProxy.Config() }),
        entry({ LocalVarRenamer() }, { LocalVarRenamer.Config() }),
        entry({ ClassRenamer() }, { ClassRenamer.Config() }),
        entry({ FieldRenamer() }, { FieldRenamer.Config() }),
        entry({ MethodRenamer() }, { MethodRenamer.Config() }),
        entry({ FakeSyntheticBridge() }, { FakeSyntheticBridge.Config() }),
        entry({ ReferenceObfuscate() }, { ReferenceObfuscate.Config() }),
        entry({ DecompilerCrasher() }, { DecompilerCrasher.Config() }),
        entry({ ShuffleMembers() }, { ShuffleMembers.Config() }),
        entry({ Watermark() }, { Watermark.Config() }),
        entry({ PostProcess() }, { PostProcess.Config() }),
    )

    private val pluginEntries = mutableListOf<TransformerRegistryEntry>()
    private var frozen = false

    val entries: List<TransformerRegistryEntry>
        get() = builtinEntries + pluginEntries

    fun register(entry: TransformerRegistryEntry) {
        registerAll(listOf(entry))
    }

    fun registerAll(entries: List<TransformerRegistryEntry>) {
        check(!frozen) {
            "Transformer registry is frozen"
        }
        val duplicated = entries
            .groupingBy { it.configClass }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .firstOrNull()
        require(duplicated == null) {
            "Transformer config ${duplicated?.qualifiedName} is registered more than once in the same plugin"
        }
        val existed = entries.firstNotNullOfOrNull { entry ->
            allEntries().firstOrNull { it.configClass == entry.configClass }?.let { existed -> entry to existed }
        }
        require(existed == null) {
            val (entry, previous) = existed!!
            "Transformer config ${entry.configClass.qualifiedName} is already registered by ${previous.owner}"
        }
        pluginEntries += entries
    }

    fun freeze() {
        frozen = true
    }

    private fun allEntries(): List<TransformerRegistryEntry> {
        return builtinEntries + pluginEntries
    }

    fun find(config: TransformerConfig): TransformerRegistryEntry? {
        return entries.firstOrNull { it.configClass == config::class }
    }

    inline fun <reified C : TransformerConfig> entry(
        noinline createTransformer: () -> Transformer<*>,
        noinline createConfig: () -> C,
        owner: String = "grunteon",
    ): TransformerRegistryEntry {
        return TransformerRegistryEntry(C::class, createTransformer, createConfig, owner)
    }
}
