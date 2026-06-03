package net.spartanb312.grunteon.obfuscator.process.transformers.controlflow

import kotlinx.serialization.Serializable
import net.spartanb312.grunt.ir.jvm.JvmIrExportOptions
import net.spartanb312.grunt.ir.jvm.JvmIrExporter
import net.spartanb312.grunt.ir.jvm.JvmIrImporter
import net.spartanb312.grunt.ir.transform.IrControlFlowFlattenOptions
import net.spartanb312.grunt.ir.transform.IrControlFlowFlattener
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
import net.spartanb312.grunteon.obfuscator.process.PipelineBuilder
import net.spartanb312.grunteon.obfuscator.process.SettingDesc
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.parForEachClassesFiltered
import net.spartanb312.grunteon.obfuscator.process.post
import net.spartanb312.grunteon.obfuscator.process.reducibleScopeValue
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter

@Transformer.Description(
    "process.controlflow.controlflow_flattening.desc",
    "Flatten method control flow through Grunt SSA IR"
)
class ControlflowFlattening : Transformer<ControlflowFlattening.Config>(
    "ControlflowFlattening",
    Category.Controlflow,
) {

    @Serializable
    data class Config(
        @SettingDesc(enText = "Specify class include/exclude rules")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc(enText = "Verify each exported method with ASM BasicInterpreter")
        val verifyExportedMethod: Boolean = true,
        @SettingDesc(enText = "Skip methods with exception handler regions")
        val skipExceptionRegions: Boolean = true,
        @SettingDesc(enText = "Keep going when one method cannot be flattened")
        val ignoreFailures: Boolean = false
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        val methodCounter = reducibleScopeValue { MergeableCounter() }
        val skippedCounter = reducibleScopeValue { MergeableCounter() }
        val failureCounter = reducibleScopeValue { MergeableCounter() }

        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            val transformedMethods = classNode.methods.map { methodNode ->
                if (methodNode.isAbstract || methodNode.isNative) {
                    methodNode
                } else {
                    runCatching {
                        methodNode.flattenControlFlow(
                            classNode.name,
                            config.verifyExportedMethod,
                            config.skipExceptionRegions
                        )
                    }.fold(
                        onSuccess = { result ->
                            if (result.changed) methodCounter.local.add() else skippedCounter.local.add()
                            result.method
                        },
                        onFailure = {
                            failureCounter.local.add()
                            if (!config.ignoreFailures) {
                                throw IllegalStateException(
                                    "Failed to flatten ${classNode.name}.${methodNode.name}${methodNode.desc}",
                                    it
                                )
                            }
                            Logger.warn("ControlflowFlattening skipped ${classNode.name}.${methodNode.name}${methodNode.desc}: ${it.message}")
                            methodNode
                        }
                    )
                }
            }
            classNode.methods.clear()
            classNode.methods.addAll(transformedMethods)
        }

        post {
            Logger.info(" - ControlflowFlattening:")
            Logger.info("    Flattened ${methodCounter.global.get()} methods through SSA IR")
            Logger.info("    Skipped ${skippedCounter.global.get()} methods")
            if (failureCounter.global.get() != 0) {
                Logger.warn("    Failed ${failureCounter.global.get()} methods")
            }
        }
    }

    private fun MethodNode.flattenControlFlow(
        ownerInternalName: String,
        verify: Boolean,
        skipExceptionRegions: Boolean
    ): FlattenedMethod {
        val imported = JvmIrImporter().import(ownerInternalName, this)
        val result = IrControlFlowFlattener(
            IrControlFlowFlattenOptions(skipExceptionRegions = skipExceptionRegions)
        ).flatten(imported.function)
        if (!result.changed) {
            return FlattenedMethod(this, changed = false)
        }

        val exported = JvmIrExporter(
            imported.metadata,
            JvmIrExportOptions(
                access = access,
                signature = signature,
                exceptions = exceptions?.toList() ?: emptyList()
            )
        ).export(imported.function)

        copyMethodMetadataTo(exported)
        if (verify) Analyzer(BasicInterpreter()).analyze(ownerInternalName, exported)
        return FlattenedMethod(exported, changed = true)
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

    private data class FlattenedMethod(
        val method: MethodNode,
        val changed: Boolean
    )
}
