package net.spartanb312.grunteon.obfuscator.process.nativecode.ir

import net.spartanb312.grunt.ir.ssa.core.SSAFunction
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAMetadata
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode

/**
 * Native lowering IR for full JVM-method support.
 *
 * The native backend needs exact JVM bytecode ordering for
 * labels, exception checks, monitor operations, stack/local slots, and JNI
 * snippets. The SSA overlay is kept when the existing importer can build it,
 * but the original instruction stream remains the source of truth for exact
 * JNI lowering.
 */
internal data class NativeJvmMethodIr(
    val ownerInternalName: String,
    val name: String,
    val desc: String,
    val access: Int,
    val maxStack: Int,
    val maxLocals: Int,
    val instructions: List<NativeJvmInstruction>,
    val tryCatchRegions: List<NativeJvmTryCatchRegion>,
    val ssaOverlay: NativeJvmSsaOverlay?,
    val ssaImportError: String?
) {
    val isStatic: Boolean
        get() = access and Opcodes.ACC_STATIC != 0

    val usesExceptionDispatch: Boolean
        get() = tryCatchRegions.isNotEmpty()

    val usesMonitor: Boolean
        get() = instructions.any {
            it.opcode == Opcodes.MONITORENTER || it.opcode == Opcodes.MONITOREXIT
        }

    val usesInvokeDynamic: Boolean
        get() = instructions.any { it.opcode == Opcodes.INVOKEDYNAMIC }

    val hasSsaOverlay: Boolean
        get() = ssaOverlay != null
}

internal data class NativeJvmSsaOverlay(
    val function: SSAFunction,
    val metadata: JvmSSAMetadata
)

internal data class NativeJvmInstruction(
    val instructionIndex: Int,
    val opcode: Int,
    val nodeType: Int,
    val node: AbstractInsnNode,
    val activeTryCatchRegionIds: List<Int>
) {
    val canThrowIntoHandler: Boolean
        get() = activeTryCatchRegionIds.isNotEmpty()
}

internal data class NativeJvmTryCatchRegion(
    val id: Int,
    val priority: Int,
    val startIndex: Int?,
    val endIndex: Int?,
    val handlerIndex: Int?,
    val caughtType: String?
)

internal enum class NativeJvmIrReadiness {
    /**
     * Full SSA overlay exists. Native codegen may use SSA for semantic analysis
     * and the JVM stream for exact JNI lowering.
     */
    HybridSsaAndJvm,

    /**
     * SSA import failed, but exact JVM stream metadata still exists. This is
     * enough for a native-obfuscator-compatible bytecode interpreter backend.
     */
    JvmOnly,

    /**
     * There are no executable JVM instructions to lower.
     */
    Empty
}

internal val NativeJvmMethodIr.readiness: NativeJvmIrReadiness
    get() = when {
        instructions.isEmpty() -> NativeJvmIrReadiness.Empty
        ssaOverlay != null -> NativeJvmIrReadiness.HybridSsaAndJvm
        else -> NativeJvmIrReadiness.JvmOnly
    }
