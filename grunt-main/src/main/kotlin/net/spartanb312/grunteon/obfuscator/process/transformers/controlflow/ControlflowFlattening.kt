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
import net.spartanb312.grunteon.obfuscator.process.seq
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isBridge
import net.spartanb312.grunteon.obfuscator.util.extensions.isInitializer
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.isSynthetic
import net.spartanb312.grunteon.obfuscator.util.extensions.methodFullDesc
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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
        @SettingDesc(enText = "Skip instance constructors because JVM uninitializedThis cannot be dispatched safely")
        val skipConstructors: Boolean = true,
        @SettingDesc(enText = "Skip synthetic and bridge methods")
        val skipSyntheticBridgeMethods: Boolean = true,
        @SettingDesc(enText = "Skip methods whose names end with \$default")
        val skipDefaultMethods: Boolean = true,
        @SettingDesc(enText = "Skip methods where uninitialized NEW objects cross control flow")
        val skipUninitializedObjectFlow: Boolean = true,
        @SettingDesc(enText = "Maximum executable instructions before skipping; 0 disables this limit")
        val maxInstructions: Int = 1000,
        @SettingDesc(enText = "Maximum estimated basic blocks before skipping; 0 disables this limit")
        val maxBasicBlocks: Int = 60,
        @SettingDesc(enText = "Maximum JVM local slots before skipping; 0 disables this limit")
        val maxLocals: Int = 64,
        @SettingDesc(enText = "Maximum estimated flattened dispatcher argument pressure before skipping; 0 disables this limit")
        val maxEstimatedDispatcherArgs: Int = 64,
        @SettingDesc(enText = "Log methods skipped by the control-flow flattening budget")
        val logBudgetSkips: Boolean = true,
        @SettingDesc(enText = "Keep going when one method cannot be flattened")
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
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy(), 1) { classNode ->
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
                                config.skipExceptionRegions
                            )
                        }
                        watchDog.remove(nameKey)
                        flattened.fold(
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
            }
            //println("[${count.getAndIncrement()}]Flattened ${classNode.name}")
            classNode.methods.clear()
            classNode.methods.addAll(transformedMethods)
        }

        seq {
            Logger.info("Took ${System.currentTimeMillis() - startTime.get()}ms")
        }

        post {
            //finished.set(true)
            Logger.info(" - ControlflowFlattening:")
            Logger.info("    Flattened ${methodCounter.global.get()} methods through SSA IR")
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
                if (insn is MethodInsnNode && insn.opcode == Opcodes.INVOKESPECIAL && insn.name == "<init>") break
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

        val basicBlocks = estimateBasicBlocks(insns)
        if (config.maxBasicBlocks > 0 && basicBlocks > config.maxBasicBlocks) {
            return "blocks=$basicBlocks > ${config.maxBasicBlocks}"
        }

        val estimatedDispatcherArgs = basicBlocks * maxLocals.coerceAtLeast(1)
        if (config.maxEstimatedDispatcherArgs > 0 && estimatedDispatcherArgs > config.maxEstimatedDispatcherArgs) {
            return "dispatcherArgs~$estimatedDispatcherArgs > ${config.maxEstimatedDispatcherArgs}"
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
