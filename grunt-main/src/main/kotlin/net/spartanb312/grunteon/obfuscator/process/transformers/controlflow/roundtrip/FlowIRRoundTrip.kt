package net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.roundtrip

import kotlinx.serialization.Serializable
import net.spartanb312.grunt.ir.flow.jvm.JvmFlowAnalyzerMode
import net.spartanb312.grunt.ir.flow.jvm.JvmFlowExporter
import net.spartanb312.grunt.ir.flow.jvm.JvmFlowImporter
import net.spartanb312.grunt.ir.flow.jvm.JvmFlowTypeHierarchy
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.hierarchy.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.hierarchy.ClassHierarchyFlowTypeHierarchy
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter

@HiddenTransformer
@Transformer.Description(
    "process.controlflow.flow_ir_round_trip.desc",
    "Round-trip methods through Grunt Flow IR"
)
class FlowIRRoundTrip : Transformer<FlowIRRoundTrip.Config>(
    "FlowIRRoundTrip",
    Category.Controlflow,
) {

    @Serializable
    data class Config(
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Verify each exported method with ASM BasicInterpreter")
        @SettingName("Verify exported method")
        val verifyExportedMethod: Boolean = true,
        @SettingDesc("Analyzer used before importing methods into Flow IR")
        @SettingName("Flow analyzer")
        val flowAnalyzer: JvmFlowAnalyzerMode = JvmFlowAnalyzerMode.Hierarchy,
        @SettingDesc("Keep going when one method cannot be imported or exported")
        @SettingName("Ignore failures")
        val ignoreFailures: Boolean = false
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        val methodCounter = reducibleScopeValue { MergeableCounter() }
        val failureCounter = reducibleScopeValue { MergeableCounter() }
        val hierarchyKey = globalScopeValue {
            if (config.flowAnalyzer == JvmFlowAnalyzerMode.Hierarchy) {
                ClassHierarchy.build(instance.workRes.allClassCollection, instance.workRes::getClassNode)
            } else {
                null
            }
        }

        parForEachClassesFiltered(
            instance.globalExclusion
                .and(instance.mixinExclusion)
                .and(config.classFilter.toClassPredicate())
        ) { classNode ->
            val flowTypeHierarchy = hierarchyKey.global?.let(::ClassHierarchyFlowTypeHierarchy) ?: JvmFlowTypeHierarchy.Empty
            val transformedMethods = classNode.methods.map { methodNode ->
                if (methodNode.isAbstract || methodNode.isNative) {
                    methodNode
                } else {
                    runCatching {
                        methodNode.roundTrip(classNode.name, config, flowTypeHierarchy)
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
                            Logger.warn("FlowIRRoundTrip skipped ${classNode.name}.${methodNode.name}${methodNode.desc}: ${it.message}")
                            methodNode
                        }
                    )
                }
            }
            classNode.methods.clear()
            classNode.methods.addAll(transformedMethods)
        }

        post {
            Logger.info(" - FlowIRRoundTrip:")
            Logger.info("    Round-tripped ${methodCounter.global.get()} methods through Flow IR")
            if (failureCounter.global.get() != 0) {
                Logger.warn("    Skipped ${failureCounter.global.get()} methods")
            }
        }
    }

    private fun MethodNode.roundTrip(
        ownerInternalName: String,
        config: Config,
        flowTypeHierarchy: JvmFlowTypeHierarchy
    ): MethodNode {
        val imported = JvmFlowImporter(
            analyzerMode = config.flowAnalyzer,
            typeHierarchy = flowTypeHierarchy
        ).import(ownerInternalName, this)
        val exported = JvmFlowExporter(imported.metadata, typeHierarchy = flowTypeHierarchy).export(imported.method)

        copyMethodMetadataTo(exported)
        if (config.verifyExportedMethod) Analyzer(BasicInterpreter()).analyze(ownerInternalName, exported)
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
