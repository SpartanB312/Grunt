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
import net.spartanb312.grunteon.obfuscator.util.extensions.isBridge
import net.spartanb312.grunteon.obfuscator.util.extensions.isInitializer
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.isSynthetic
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import java.util.concurrent.atomic.AtomicInteger

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
        @SettingDesc(enText = "Skip compiler-generated synthetic and bridge methods")
        val skipSyntheticMethods: Boolean = true,
        @SettingDesc(enText = "Skip methods where uninitialized NEW objects cross control flow")
        val skipUninitializedObjectFlow: Boolean = true,
        @SettingDesc(enText = "Keep going when one method cannot be flattened")
        val ignoreFailures: Boolean = false
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        val methodCounter = reducibleScopeValue { MergeableCounter() }
        val skippedCounter = reducibleScopeValue { MergeableCounter() }
        val failureCounter = reducibleScopeValue { MergeableCounter() }
        val count = AtomicInteger(0)
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy(), 1) { classNode ->
            val transformedMethods = classNode.methods.map { methodNode ->
                if (methodNode.isAbstract || methodNode.isNative) {
                    methodNode
                } else if (config.skipConstructors && methodNode.isInitializer) {
                    skippedCounter.local.add()
                    methodNode
                } else if (config.skipSyntheticMethods && methodNode.isCompilerGeneratedHelper) {
                    skippedCounter.local.add()
                    methodNode
                } else if (config.skipUninitializedObjectFlow && methodNode.hasUninitializedObjectAcrossControlFlow()) {
                    skippedCounter.local.add()
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
            println("[${count.getAndIncrement()}]Flattened ${classNode.name}")
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

    private val MethodNode.isCompilerGeneratedHelper: Boolean
        get() = isSynthetic || isBridge || name.endsWith("\$default")

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
