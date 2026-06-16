package net.spartanb312.grunteon.obfuscator.process.nativecode.ir

import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.VarInsnNode

/**
 * Capability analysis for the native backend.
 *
 * This is intentionally broader than the current v1 expression backend. It
 * treats try/catch and monitor instructions as supported full-JVM lowering
 * features, handle them through JNI exception dispatch
 */
internal object NativeJvmSupportAnalyzer {

    fun analyze(ir: NativeJvmMethodIr): NativeJvmSupportReport {
        val issues = mutableListOf<NativeJvmSupportIssue>()
        val features = mutableSetOf<NativeJvmFeature>()

        if (ir.instructions.isEmpty()) {
            issues += NativeJvmSupportIssue(
                kind = NativeJvmSupportIssueKind.EmptyMethod,
                detail = "method has no executable JVM instructions"
            )
        }

        if (ir.tryCatchRegions.isNotEmpty()) {
            features += NativeJvmFeature.TryCatch
            ir.tryCatchRegions.forEach { region ->
                if (region.startIndex == null || region.endIndex == null || region.handlerIndex == null) {
                    issues += NativeJvmSupportIssue(
                        kind = NativeJvmSupportIssueKind.InvalidTryCatchBoundary,
                        detail = "try/catch region ${region.id} has an unresolved boundary"
                    )
                }
            }
        }

        if (ir.usesMonitor) features += NativeJvmFeature.Monitor
        if (ir.ssaOverlay == null && ir.instructions.isNotEmpty()) {
            features += NativeJvmFeature.JvmOnlyWithoutSsa
        }

        ir.instructions.forEach { instruction ->
            when (instruction.opcode) {
                Opcodes.MONITORENTER,
                Opcodes.MONITOREXIT -> features += NativeJvmFeature.Monitor
                Opcodes.INVOKEDYNAMIC -> {
                    features += NativeJvmFeature.InvokeDynamic
                    issues += NativeJvmSupportIssue(
                        kind = NativeJvmSupportIssueKind.InvokeDynamicStillPresent,
                        instruction = instruction,
                        detail = "invokedynamic must be bridged before full JVM native lowering"
                    )
                }
                Opcodes.JSR -> issues += NativeJvmSupportIssue(
                    kind = NativeJvmSupportIssueKind.UnsupportedLegacySubroutine,
                    instruction = instruction,
                    detail = "JSR bytecode is not supported"
                )
                Opcodes.RET -> issues += NativeJvmSupportIssue(
                    kind = NativeJvmSupportIssueKind.UnsupportedLegacySubroutine,
                    instruction = instruction,
                    detail = "RET bytecode is not supported"
                )
            }

            when (val node = instruction.node) {
                is JumpInsnNode -> {
                    if (node.opcode == Opcodes.JSR) {
                        issues += NativeJvmSupportIssue(
                            kind = NativeJvmSupportIssueKind.UnsupportedLegacySubroutine,
                            instruction = instruction,
                            detail = "JSR jump is not supported"
                        )
                    }
                }
                is VarInsnNode -> {
                    if (node.opcode == Opcodes.RET) {
                        issues += NativeJvmSupportIssue(
                            kind = NativeJvmSupportIssueKind.UnsupportedLegacySubroutine,
                            instruction = instruction,
                            detail = "RET local subroutine return is not supported"
                        )
                    }
                }
                is LdcInsnNode -> analyzeLdc(node, instruction, features, issues)
                is InvokeDynamicInsnNode -> Unit
            }
        }

        return NativeJvmSupportReport(
            readiness = ir.readiness,
            features = features,
            issues = issues.distinct()
        )
    }

    private fun analyzeLdc(
        node: LdcInsnNode,
        instruction: NativeJvmInstruction,
        features: MutableSet<NativeJvmFeature>,
        issues: MutableList<NativeJvmSupportIssue>
    ) {
        when (val cst = node.cst) {
            is ConstantDynamic -> {
                features += NativeJvmFeature.ConstantDynamic
                issues += NativeJvmSupportIssue(
                    kind = NativeJvmSupportIssueKind.ConstantDynamicStillPresent,
                    instruction = instruction,
                    detail = "constant dynamic needs a bridge/preprocessor before full JVM native lowering"
                )
            }
            is Handle -> {
                features += NativeJvmFeature.MethodHandleConstant
            }
            is Type -> {
                if (cst.sort == Type.METHOD) {
                    features += NativeJvmFeature.MethodTypeConstant
                }
            }
        }
    }
}

internal data class NativeJvmSupportReport(
    val readiness: NativeJvmIrReadiness,
    val features: Set<NativeJvmFeature>,
    val issues: List<NativeJvmSupportIssue>
) {
    val isFullJvmLoweringReady: Boolean
        get() = readiness != NativeJvmIrReadiness.Empty && issues.isEmpty()
}

internal data class NativeJvmSupportIssue(
    val kind: NativeJvmSupportIssueKind,
    val instruction: NativeJvmInstruction? = null,
    val detail: String
)

internal enum class NativeJvmSupportIssueKind {
    EmptyMethod,
    InvalidTryCatchBoundary,
    UnsupportedLegacySubroutine,
    InvokeDynamicStillPresent,
    ConstantDynamicStillPresent
}

internal enum class NativeJvmFeature {
    TryCatch,
    Monitor,
    InvokeDynamic,
    ConstantDynamic,
    MethodHandleConstant,
    MethodTypeConstant,
    JvmOnlyWithoutSsa
}
