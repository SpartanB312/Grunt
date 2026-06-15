package net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.exp

import kotlinx.serialization.Serializable
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAExportOptions
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAExporter
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAImporter
import net.spartanb312.grunt.ir.ssa.transform.SSARegionControlFlowFlattenOptions
import net.spartanb312.grunt.ir.ssa.transform.SSARegionControlFlowFlattener
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.extensions.*
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@HiddenTransformer
@Transformer.Stability(StableLevel.Experimental)
@Transformer.Description(
    "process.controlflow.controlflow_flattening.desc",
    "Flatten method control flow through Grunt SSA IR"
)
class ControlflowFlatteningSSA : Transformer<ControlflowFlatteningSSA.Config>(
    "ControlflowFlatteningSSA",
    Category.Controlflow,
) {

    @Serializable
    data class Config(
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Verify each exported method with ASM BasicInterpreter")
        @SettingName("Verify exported method")
        val verifyExportedMethod: Boolean = true,
        @SettingDesc("Skip exception protected/handler regions")
        @SettingName("Skip exception regions")
        val skipExceptionRegions: Boolean = false,
        @SettingDesc("Skip instance constructors because JVM uninitializedThis cannot be dispatched safely")
        @SettingName("Skip constructors")
        val skipConstructors: Boolean = true,
        @SettingDesc("Skip synthetic and bridge methods")
        @SettingName("Skip synthetic bridge methods")
        val skipSyntheticBridgeMethods: Boolean = true,
        @SettingDesc("Skip methods whose names end with \$default")
        @SettingName("Skip default methods")
        val skipDefaultMethods: Boolean = true,
        @SettingDesc("Skip methods where uninitialized NEW objects cross control flow")
        @SettingName("Skip uninitialized object flow")
        val skipUninitializedObjectFlow: Boolean = true,
        @SettingDesc("Maximum executable instructions before skipping; 0 disables this limit")
        @SettingName("Max instructions")
        val maxInstructions: Int = 1000,
        @SettingDesc("Maximum estimated method basic blocks before skipping; 0 disables this limit")
        @SettingName("Max basic blocks")
        val maxBasicBlocks: Int = 0,
        @SettingDesc("Maximum JVM local slots before skipping; 0 disables this limit")
        @SettingName("Max locals")
        val maxLocals: Int = 64,
        @SettingDesc("Minimum basic blocks in one flattened region")
        @SettingName("Min region blocks")
        val minRegionBlocks: Int = 2,
        @SettingDesc("Maximum basic blocks in one flattened region")
        @SettingName("Max region blocks")
        val maxRegionBlocks: Int = 8,
        @SettingDesc("Maximum estimated region dispatcher argument pressure before skipping that region; 0 disables this limit")
        @SettingName("Max estimated dispatcher args")
        val maxEstimatedDispatcherArgs: Int = 64,
        @SettingDesc("Parallel class processing batch size")
        @SettingName("Parallel batch size")
        val parallelBatchSize: Int = 16,
        @SettingDesc("Log methods skipped by the control-flow flattening budget")
        @SettingName("Log budget skips")
        val logBudgetSkips: Boolean = true,
        @SettingDesc("Keep going when one method cannot be flattened")
        @SettingName("Ignore failures")
        val ignoreFailures: Boolean = false
    ) : TransformerConfig()

    private val watchDog = ConcurrentHashMap<String, Long>()
    private val startTime = AtomicLong(0)

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        seq {
            startTime.set(System.currentTimeMillis())
        }
        val methodCounter = reducibleScopeValue { MergeableCounter() }
        val regionCounter = reducibleScopeValue { MergeableCounter() }
        val skippedRegionCounter = reducibleScopeValue { MergeableCounter() }
        val skippedCounter = reducibleScopeValue { MergeableCounter() }
        val budgetSkippedCounter = reducibleScopeValue { MergeableCounter() }
        val failureCounter = reducibleScopeValue { MergeableCounter() }
        /*val count = AtomicInteger(0)
        val finished = AtomicBoolean(false)
        Thread({
            while (!finished.get()) {
                if (watchDog.isNotEmpty()) {
                    println("WatchDog: ")
                    watchDog.forEach { (name, startTime) ->
                        println("Watchdog: $name, ${System.currentTimeMillis() - startTime}ms")
                    }
                }
                Thread.sleep(1000)
            }
        }, "ControlflowFlattening-Watchdog").apply {
            isDaemon = true
            start()
        }*/
        parForEachClassesFiltered(
            config.classFilter.buildFilterStrategy(),
            config.parallelBatchSize.coerceAtLeast(1)
        ) { classNode ->
            val transformedMethods = classNode.methods.map { methodNode ->
                if (methodNode.isAbstract || methodNode.isNative) {
                    methodNode
                } else if (config.skipConstructors && methodNode.isInitializer) {
                    skippedCounter.local.add()
                    methodNode
                } else if (config.skipSyntheticBridgeMethods && methodNode.isSyntheticOrBridge) {
                    skippedCounter.local.add()
                    methodNode
                } else if (config.skipDefaultMethods && methodNode.isDefaultHelper) {
                    skippedCounter.local.add()
                    methodNode
                } else if (config.skipUninitializedObjectFlow && methodNode.hasUninitializedObjectAcrossControlFlow()) {
                    skippedCounter.local.add()
                    methodNode
                } else {
                    val nameKey = methodFullDesc(classNode, methodNode)
                    val budgetSkipReason = methodNode.flattenBudgetSkipReason(config)
                    if (budgetSkipReason != null) {
                        skippedCounter.local.add()
                        budgetSkippedCounter.local.add()
                        if (config.logBudgetSkips) {
                            Logger.debug("ControlflowFlattening budget skipped $nameKey: $budgetSkipReason")
                        }
                        methodNode
                    } else {
                        watchDog[nameKey] = System.currentTimeMillis()
                        val flattened = runCatching {
                            methodNode.flattenControlFlow(
                                classNode.name,
                                config.verifyExportedMethod,
                                config.skipExceptionRegions,
                                config.minRegionBlocks,
                                config.maxRegionBlocks,
                                config.maxEstimatedDispatcherArgs
                            )
                        }
                        watchDog.remove(nameKey)
                        flattened.fold(
                            onSuccess = { result ->
                                if (result.changed) {
                                    methodCounter.local.add()
                                    regionCounter.local.add(result.flattenedRegions)
                                } else {
                                    skippedCounter.local.add()
                                    if (config.logBudgetSkips && result.reason != null) {
                                        Logger.debug("ControlflowFlattening IR skipped $nameKey: ${result.reason}")
                                    }
                                }
                                skippedRegionCounter.local.add(result.skippedRegions)
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
            //println("[${count.getAndIncrement()}]Flattened ${classNode.name}")
            classNode.methods.clear()
            classNode.methods.addAll(transformedMethods)
        }

        seq {
            Logger.info(" ${System.currentTimeMillis() - startTime.get()}ms")
        }

        post {
            //finished.set(true)
            Logger.info(" - ControlflowFlattening:")
            Logger.info("    Flattened ${methodCounter.global.get()} methods through region SSA IR")
            Logger.info("    Flattened ${regionCounter.global.get()} regions")
            if (skippedRegionCounter.global.get() != 0) {
                Logger.info("    Skipped ${skippedRegionCounter.global.get()} regions")
            }
            Logger.info("    Skipped ${skippedCounter.global.get()} methods")
            if (budgetSkippedCounter.global.get() != 0) {
                Logger.info("    Budget-skipped ${budgetSkippedCounter.global.get()} methods")
            }
            if (failureCounter.global.get() != 0) {
                Logger.warn("    Failed ${failureCounter.global.get()} methods")
            }
        }
    }

    private val MethodNode.isSyntheticOrBridge: Boolean
        get() = isSynthetic || isBridge

    private val MethodNode.isDefaultHelper: Boolean
        get() = name.endsWith("\$default")

    private fun MethodNode.hasUninitializedObjectAcrossControlFlow(): Boolean {
        val insns = instructions?.toArray() ?: return false
        for (startIndex in insns.indices) {
            if (insns[startIndex].opcode != Opcodes.NEW) continue

            for (index in startIndex + 1 until insns.size) {
                val insn = insns[index]
                if (insn.opcode < 0) continue
                if (insn.isControlFlowTransfer()) return true
                if (insn is MethodInsnNode && insn.opcode == Opcodes.INVOKESPECIAL && insn.name == "<init>") {
                    break
                }
            }
        }
        return false
    }

    private fun MethodNode.flattenBudgetSkipReason(config: Config): String? {
        val insns = instructions?.toArray() ?: return "no bytecode"
        val executableInstructions = insns.count { it.opcode >= 0 }
        if (executableInstructions == 0) return "no executable bytecode"
        if (config.maxInstructions > 0 && executableInstructions > config.maxInstructions) {
            return "instructions=$executableInstructions > ${config.maxInstructions}"
        }
        if (config.maxLocals > 0 && maxLocals > config.maxLocals) {
            return "locals=$maxLocals > ${config.maxLocals}"
        }

        if (config.maxBasicBlocks > 0) {
            val basicBlocks = estimateBasicBlocks(insns)
            if (basicBlocks > config.maxBasicBlocks) {
                return "blocks=$basicBlocks > ${config.maxBasicBlocks}"
            }
        }
        return null
    }

    private fun estimateBasicBlocks(insns: Array<AbstractInsnNode>): Int {
        fun nextExecutableIndex(fromIndex: Int): Int? {
            for (index in fromIndex until insns.size) {
                if (insns[index].opcode >= 0) return index
            }
            return null
        }

        val firstInstruction = nextExecutableIndex(0) ?: return 0
        val blockStarts = sortedSetOf(firstInstruction)

        fun addNextExecutable(fromIndex: Int) {
            nextExecutableIndex(fromIndex)?.let { blockStarts.add(it) }
        }

        fun addLabel(label: LabelNode?) {
            if (label == null) return
            val labelIndex = insns.indexOf(label)
            if (labelIndex >= 0) addNextExecutable(labelIndex)
        }

        insns.forEachIndexed { index, insn ->
            when (insn) {
                is JumpInsnNode -> {
                    addLabel(insn.label)
                    addNextExecutable(index + 1)
                }
                is TableSwitchInsnNode -> {
                    addLabel(insn.dflt)
                    insn.labels.forEach(::addLabel)
                    addNextExecutable(index + 1)
                }
                is LookupSwitchInsnNode -> {
                    addLabel(insn.dflt)
                    insn.labels.forEach(::addLabel)
                    addNextExecutable(index + 1)
                }
                else -> if (insn.opcode.isBlockTerminatorOpcode()) {
                    addNextExecutable(index + 1)
                }
            }
        }

        return blockStarts.size
    }

    private fun Int.isBlockTerminatorOpcode(): Boolean {
        return this == Opcodes.GOTO ||
            this == Opcodes.JSR ||
            this == Opcodes.RET ||
            this in Opcodes.IRETURN..Opcodes.RETURN ||
            this == Opcodes.ATHROW
    }

    private fun AbstractInsnNode.isControlFlowTransfer(): Boolean {
        return this is JumpInsnNode || this is TableSwitchInsnNode || this is LookupSwitchInsnNode
    }

    private fun MethodNode.flattenControlFlow(
        ownerInternalName: String,
        verify: Boolean,
        skipExceptionRegions: Boolean,
        minRegionBlocks: Int,
        maxRegionBlocks: Int,
        maxDispatcherArgs: Int
    ): FlattenedMethod {
        val imported = JvmSSAImporter().import(ownerInternalName, this)
        val minBlocks = minRegionBlocks.coerceAtLeast(1)
        val maxBlocks = maxRegionBlocks.coerceAtLeast(minBlocks)
        val result = SSARegionControlFlowFlattener(
            SSARegionControlFlowFlattenOptions(
                skipExceptionRegions = skipExceptionRegions,
                minRegionBlocks = minBlocks,
                maxRegionBlocks = maxBlocks,
                maxDispatcherArgs = maxDispatcherArgs
            )
        ).flatten(imported.function)
        if (!result.changed) {
            return FlattenedMethod(this, changed = false, skippedRegions = result.skippedRegions, reason = result.reason)
        }

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
        return FlattenedMethod(
            exported,
            changed = true,
            flattenedRegions = result.flattenedRegions,
            skippedRegions = result.skippedRegions
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
        val skippedRegions: Int = 0,
        val reason: String? = null
    )
}