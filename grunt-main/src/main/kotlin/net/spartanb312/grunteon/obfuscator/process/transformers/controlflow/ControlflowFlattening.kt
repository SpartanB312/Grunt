package net.spartanb312.grunteon.obfuscator.process.transformers.controlflow

import kotlinx.serialization.Serializable
import net.spartanb312.grunt.ir.flow.jvm.JvmFlowExportOptions
import net.spartanb312.grunt.ir.flow.jvm.JvmFlowExporter
import net.spartanb312.grunt.ir.flow.jvm.JvmFlowImporter
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
import net.spartanb312.grunteon.obfuscator.process.DecimalRangeVal
import net.spartanb312.grunteon.obfuscator.process.IntRangeVal
import net.spartanb312.grunteon.obfuscator.process.PipelineBuilder
import net.spartanb312.grunteon.obfuscator.process.SettingDesc
import net.spartanb312.grunteon.obfuscator.process.SettingName
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.globalScopeValue
import net.spartanb312.grunteon.obfuscator.process.hierarchy.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.parForEachClassesFiltered
import net.spartanb312.grunteon.obfuscator.process.post
import net.spartanb312.grunteon.obfuscator.process.reducibleScopeValue
import net.spartanb312.grunteon.obfuscator.process.seq
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.junkcode.JunkCallPool
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.junkcode.JunkCodeOptions
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.process.FlowControlFlowFlattenOptions
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.process.FlowControlFlowFlattener
import net.spartanb312.grunteon.obfuscator.process.transformers.other.FakeSyntheticBridge
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isBridge
import net.spartanb312.grunteon.obfuscator.util.extensions.isInitializer
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.isSynthetic
import net.spartanb312.grunteon.obfuscator.util.extensions.methodFullDesc
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import java.util.concurrent.atomic.AtomicLong

@Transformer.Description(
    "process.controlflow.controlflow_flattening.desc",
    "Flatten method control flow through Flow IR dispatcher islands"
)
class ControlflowFlattening : Transformer<ControlflowFlattening.Config>(
    "ControlflowFlattening",
    Category.Controlflow,
) {

    init {
        before(FakeSyntheticBridge::class.java, "ControlflowFlattening should run before FakeSyntheticBridge")
        before(ControlflowJump::class.java, "ControlflowFlattening should run before ControlflowJump")
    }

    @Serializable
    data class Config(
        @SettingDesc("Specify class include/exclude rules")
        @SettingName("Class filter")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Run ASM BasicInterpreter after exporting Flow IR bytecode")
        @SettingName("Verify bytecode")
        val verifyBytecode: Boolean = true,
        @SettingDesc("Include method entry block in dispatcher flattening")
        @SettingName("Include method entry")
        val includeMethodEntry: Boolean = true,
        @SettingDesc("Include blocks protected by try/catch regions and exception handlers")
        @SettingName("Include exception blocks")
        val includeExceptionBlocks: Boolean = true,
        @SettingDesc("Include JVM constructors")
        @SettingName("Include constructors")
        val includeConstructors: Boolean = false,
        @SettingDesc("Include synthetic or bridge methods")
        @SettingName("Include synthetic bridge methods")
        val includeSyntheticBridgeMethods: Boolean = true,
        @SettingDesc("Include Kotlin/JVM default helper methods")
        @SettingName("Include default helper methods")
        val includeDefaultHelperMethods: Boolean = true,
        @SettingDesc("Allow Flow blocks whose verifier frame contains uninitialized values")
        @SettingName("Include uninitialized frames")
        val includeUninitializedFrames: Boolean = false,
        @SettingDesc("Minimum number of Flow blocks required before flattening a method")
        @SettingName("Min flattened blocks")
        val minFlattenedBlocks: Int = 2,
        @SettingDesc("Maximum Flow blocks inside the maximal region; 0 expands to all eligible blocks")
        @SettingName("Max flattened blocks")
        val maxFlattenedBlocks: Int = 0,
        @SettingDesc("Maximum verifier-frame dispatcher islands in the maximal region; 0 allows all islands")
        @SettingName("Max dispatcher islands")
        val maxDispatcherIslands: Int = 0,
        @SettingDesc("Bogus switch cases inserted into each dispatcher island")
        @SettingName("Fake cases per dispatcher")
        val fakeCasesPerDispatcher: Int = 1,
        @SettingDesc("Allow dispatcher fake cases to terminate through generated JunkCode")
        @SettingName("Junk cases")
        val junkCases: Boolean = true,
        @SettingDesc("Chance that one fake dispatcher case becomes a terminal JunkCode case. Range: 0.0..1.0")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Junk case chance")
        val junkCaseChance: Double = 0.35,
        @SettingDesc("Maximum junk call preludes emitted before a junk terminal return")
        @IntRangeVal(min = 0, max = 8)
        @SettingName("Max junk prelude calls")
        val maxJunkPreludeCalls: Int = 2,
        @SettingDesc("Use public static methods as junk return values when hierarchy proves assignability")
        @SettingName("Assignable junk returns")
        val assignableJunkReturns: Boolean = true,
        @SettingDesc("Use natural reference return values such as strings, wrappers, and empty arrays")
        @SettingName("Natural reference values")
        val naturalReferenceValues: Boolean = true,
        @SettingDesc("Allow library classes to be scanned for junk call candidates")
        @SettingName("Expanded junk calls")
        val expandedJunkCalls: Boolean = false,
        @SettingDesc("Minimum arithmetic state operations used to compute a dispatcher case key")
        @SettingName("Min state ops per case")
        val minStateOpsPerCase: Int = 2,
        @SettingDesc("Maximum arithmetic state operations used to compute a dispatcher case key")
        @SettingName("Max state ops per case")
        val maxStateOpsPerCase: Int = 5,
        @SettingDesc("Shuffle physical layout order of Flow blocks selected into the flattened region")
        @SettingName("Shuffle region blocks")
        val shuffleRegionBlocks: Boolean = false,
        @SettingDesc("Maximum executable JVM instructions before importing Flow IR; 0 disables this limit")
        @SettingName("Max executable instructions")
        val maxExecutableInstructions: Int = 0,
        @SettingDesc("Maximum imported Flow blocks before flattening; 0 disables this limit")
        @SettingName("Max flow blocks")
        val maxFlowBlocks: Int = 0,
        @SettingDesc("Parallel class processing batch size")
        @SettingName("Worker batch size")
        val workerBatchSize: Int = 16,
        @SettingDesc("Log methods skipped by Flow IR CFF planning")
        @SettingName("Log skips")
        val logSkips: Boolean = true,
        @SettingDesc("Keep going when one method cannot be flattened")
        @SettingName("Ignore failures")
        val ignoreFailures: Boolean = false
    ) : TransformerConfig()

    private val startTime = AtomicLong(0L)

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        seq {
            startTime.set(System.currentTimeMillis())
        }

        val methodCounter = reducibleScopeValue { MergeableCounter() }
        val regionCounter = reducibleScopeValue { MergeableCounter() }
        val blockCounter = reducibleScopeValue { MergeableCounter() }
        val dispatcherCounter = reducibleScopeValue { MergeableCounter() }
        val bridgeCounter = reducibleScopeValue { MergeableCounter() }
        val edgeCounter = reducibleScopeValue { MergeableCounter() }
        val fakeCaseCounter = reducibleScopeValue { MergeableCounter() }
        val skippedCounter = reducibleScopeValue { MergeableCounter() }
        val failureCounter = reducibleScopeValue { MergeableCounter() }
        val hierarchyKey = globalScopeValue<ClassHierarchy?> {
            if (config.junkCases) {
                ClassHierarchy.build(instance.workRes.allClassCollection, instance.workRes::getClassNode)
            } else {
                null
            }
        }
        val junkCallPoolKey = globalScopeValue<JunkCallPool?> {
            if (config.junkCases) {
                val classes = if (config.expandedJunkCalls) {
                    instance.workRes.allClassCollection
                } else {
                    instance.workRes.inputClassCollection
                }
                JunkCallPool.build(classes)
            } else {
                null
            }
        }

        parForEachClassesFiltered(config.classFilter.buildFilterStrategy(), config.workerBatchSize.coerceAtLeast(1)) { classNode ->
            val transformedMethods = classNode.methods.map { methodNode ->
                if (methodNode.isAbstract || methodNode.isNative) {
                    methodNode
                } else {
                    val nameKey = methodFullDesc(classNode, methodNode)
                    val preImportSkip = methodNode.preImportSkipReason(config)
                    if (preImportSkip != null) {
                        skippedCounter.local.add()
                        if (config.logSkips) Logger.debug("ControlflowFlattening skipped $nameKey: $preImportSkip")
                        methodNode
                    } else {
                        runCatching {
                            val randomGen = Xoshiro256PPRandom(
                                getSeed(classNode.name, methodNode.name, methodNode.desc, "FlowControlFlowFlattening")
                            )
                            methodNode.flattenControlFlow(
                                classNode.name,
                                config,
                                randomGen,
                                hierarchyKey.global,
                                junkCallPoolKey.global
                            )
                        }.fold(
                            onSuccess = { result ->
                                if (result.changed) {
                                    methodCounter.local.add()
                                    regionCounter.local.add(result.flattenedRegions)
                                    blockCounter.local.add(result.flattenedBlocks)
                                    dispatcherCounter.local.add(result.dispatcherIslands)
                                    bridgeCounter.local.add(result.stateBridges)
                                    edgeCounter.local.add(result.rewrittenEdges)
                                    fakeCaseCounter.local.add(result.fakeCases)
                                } else {
                                    skippedCounter.local.add()
                                    if (config.logSkips) Logger.debug("ControlflowFlattening skipped $nameKey: ${result.reason}")
                                }
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
            }
            classNode.methods.clear()
            classNode.methods.addAll(transformedMethods)
        }

        seq {
            Logger.info("Took ${System.currentTimeMillis() - startTime.get()}ms")
        }

        post {
            Logger.info(" - ControlflowFlattening:")
            Logger.info("    Flattened ${methodCounter.global.get()} methods through Flow IR")
            Logger.info("    Flattened ${regionCounter.global.get()} maximal Flow regions")
            Logger.info("    Flattened ${blockCounter.global.get()} Flow blocks")
            Logger.info("    Created ${dispatcherCounter.global.get()} dispatcher islands")
            Logger.info("    Created ${bridgeCounter.global.get()} state bridges")
            Logger.info("    Rewritten ${edgeCounter.global.get()} incoming edges")
            Logger.info("    Created ${fakeCaseCounter.global.get()} fake dispatcher cases")
            Logger.info("    Skipped ${skippedCounter.global.get()} methods")
            if (failureCounter.global.get() != 0) {
                Logger.warn("    Failed ${failureCounter.global.get()} methods")
            }
        }
    }

    private fun MethodNode.preImportSkipReason(config: Config): String? {
        if (!config.includeConstructors && isInitializer) return "constructor"
        if (!config.includeSyntheticBridgeMethods && (isSynthetic || isBridge)) return "synthetic or bridge method"
        if (!config.includeDefaultHelperMethods && name.endsWith("\$default")) return "default helper method"

        val executable = executableInstructionCount()
        if (executable == 0) return "no executable bytecode"
        if (config.maxExecutableInstructions > 0 && executable > config.maxExecutableInstructions) {
            return "instructions=$executable > ${config.maxExecutableInstructions}"
        }
        return null
    }

    private fun MethodNode.executableInstructionCount(): Int {
        return instructions?.toArray()?.count { it.opcode >= 0 } ?: 0
    }

    private fun MethodNode.flattenControlFlow(
        ownerInternalName: String,
        config: Config,
        randomGen: UniformRandomProvider,
        hierarchy: ClassHierarchy?,
        junkCallPool: JunkCallPool?
    ): FlattenedMethod {
        val imported = JvmFlowImporter().import(ownerInternalName, this)
        if (config.maxFlowBlocks > 0 && imported.method.blocks.size > config.maxFlowBlocks) {
            return FlattenedMethod(this, changed = false, reason = "flowBlocks=${imported.method.blocks.size} > ${config.maxFlowBlocks}")
        }

        val result = FlowControlFlowFlattener(
            FlowControlFlowFlattenOptions(
                includeMethodEntry = config.includeMethodEntry,
                includeExceptionBlocks = config.includeExceptionBlocks,
                includeUninitializedFrames = config.includeUninitializedFrames,
                minFlattenedBlocks = config.minFlattenedBlocks,
                maxFlattenedBlocks = config.maxFlattenedBlocks,
                maxDispatcherIslands = config.maxDispatcherIslands,
                fakeCasesPerDispatcher = config.fakeCasesPerDispatcher,
                junkCases = config.junkCases,
                junkCaseChance = config.junkCaseChance,
                junkCodeOptions = JunkCodeOptions(
                    maxPreludeCalls = config.maxJunkPreludeCalls.coerceAtLeast(0),
                    useJunkCallPrelude = junkCallPool?.isEmpty() == false,
                    useNaturalReferenceValues = config.naturalReferenceValues,
                    useAssignableJunkReturns = config.assignableJunkReturns,
                    junkReturnChance = 0.35
                ),
                minStateOpsPerCase = config.minStateOpsPerCase,
                maxStateOpsPerCase = config.maxStateOpsPerCase,
                shuffleRegionBlocks = config.shuffleRegionBlocks
            ),
            randomGen,
            hierarchy,
            junkCallPool
        ).flatten(imported.method)
        if (!result.changed) {
            return FlattenedMethod(this, changed = false, reason = result.reason)
        }

        val exported = JvmFlowExporter(
            imported.metadata,
            JvmFlowExportOptions(
                access = access,
                signature = signature,
                exceptions = exceptions?.toList() ?: emptyList()
            )
        ).export(imported.method)
        copyMethodMetadataTo(exported)

        if (config.verifyBytecode) {
            Analyzer(BasicInterpreter()).analyze(ownerInternalName, exported)
        }

        return FlattenedMethod(
            method = exported,
            changed = true,
            flattenedRegions = result.flattenedRegions,
            flattenedBlocks = result.flattenedBlocks,
            dispatcherIslands = result.dispatcherIslands,
            stateBridges = result.stateBridges,
            rewrittenEdges = result.rewrittenEdges,
            fakeCases = result.fakeCases
        )
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
        val changed: Boolean,
        val flattenedRegions: Int = 0,
        val flattenedBlocks: Int = 0,
        val dispatcherIslands: Int = 0,
        val stateBridges: Int = 0,
        val rewrittenEdges: Int = 0,
        val fakeCases: Int = 0,
        val reason: String? = null
    )
}
