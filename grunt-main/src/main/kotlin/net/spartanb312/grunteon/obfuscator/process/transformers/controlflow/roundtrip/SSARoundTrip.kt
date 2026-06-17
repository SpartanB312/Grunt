package net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.roundtrip

import kotlinx.serialization.Serializable
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAExportOptions
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAExporter
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAImporter
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter

@HiddenTransformer
@Transformer.Description(
    "process.controlflow.ssa_round_trip.desc",
    "Round-trip methods through Grunt SSA IR"
)
class SSARoundTrip : Transformer<SSARoundTrip.Config>(
    "SSARoundTrip",
    Category.Controlflow,
) {

    @Serializable
    data class Config(
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Verify each exported method with ASM BasicInterpreter")
        @SettingName("Verify exported method")
        val verifyExportedMethod: Boolean = true,
        @SettingDesc("Keep going when one method cannot be imported or exported")
        @SettingName("Ignore failures")
        val ignoreFailures: Boolean = false
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        val methodCounter = reducibleScopeValue { MergeableCounter() }
        val failureCounter = reducibleScopeValue { MergeableCounter() }

        parForEachClassesFiltered(
            instance.globalExclusion
                .and(instance.mixinExclusion)
                .and(config.classFilter.toClassPredicate())
        ) { classNode ->
            val transformedMethods = classNode.methods.map { methodNode ->
                if (methodNode.isAbstract || methodNode.isNative) {
                    methodNode
                } else {
                    runCatching {
                        methodNode.roundTrip(classNode.name, config.verifyExportedMethod)
                    }.fold(
                        onSuccess = {
                            methodCounter.local.add()
                            it
                        },
                        onFailure = {
                            failureCounter.local.add()
                            if (!config.ignoreFailures) {
                                throw IllegalStateException(
                                    "Failed to round-trip ${classNode.name}.${methodNode.name}${methodNode.desc}",
                                    it
                                )
                            }
                            Logger.warn("SsaRoundTrip skipped ${classNode.name}.${methodNode.name}${methodNode.desc}: ${it.message}")
                            methodNode
                        }
                    )
                }
            }
            classNode.methods.clear()
            classNode.methods.addAll(transformedMethods)
        }

        post {
            Logger.info(" - SsaRoundTrip:")
            Logger.info("    Round-tripped ${methodCounter.global.get()} methods through SSA IR")
            if (failureCounter.global.get() != 0) {
                Logger.warn("    Skipped ${failureCounter.global.get()} methods")
            }
        }
    }

    private fun MethodNode.roundTrip(ownerInternalName: String, verify: Boolean): MethodNode {
        val imported = JvmSSAImporter().import(ownerInternalName, this)
        val exported = JvmSSAExporter(
            imported.metadata,
            JvmSSAExportOptions(
                access = access,
                signature = signature,
                exceptions = exceptions?.toList() ?: emptyList()
            )
        ).export(imported.function)

        copyMethodMetadataTo(exported)
        if (verify) Analyzer(BasicInterpreter()).analyze(ownerInternalName, exported)
        return exported
    }

    private fun MethodNode.copyMethodMetadataTo(target: MethodNode) {
        target.parameters = parameters
        target.visibleAnnotations = visibleAnnotations
        target.invisibleAnnotations = invisibleAnnotations
        target.visibleTypeAnnotations = visibleTypeAnnotations
        target.invisibleTypeAnnotations = invisibleTypeAnnotations
        target.visibleParameterAnnotations = visibleParameterAnnotations
        target.invisibleParameterAnnotations = invisibleParameterAnnotations
        target.visibleAnnotableParameterCount = visibleAnnotableParameterCount
        target.invisibleAnnotableParameterCount = invisibleAnnotableParameterCount
        target.annotationDefault = annotationDefault
        target.attrs = attrs
    }
}
