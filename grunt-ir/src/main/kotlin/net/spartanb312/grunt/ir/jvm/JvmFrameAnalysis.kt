package net.spartanb312.grunt.ir.jvm

import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame
import org.objectweb.asm.tree.analysis.Interpreter

internal data class JvmAnalyzedFrames(
    val frames: Array<Frame<BasicValue>?>,
    val maxLocals: Int,
    val maxStack: Int
)

/** Compute stale maxs with ASM's dynamically growing frames, without mutating the caller's metadata. */
internal fun analyzeJvmFrames(
    owner: String,
    method: MethodNode,
    interpreter: Interpreter<BasicValue>
): JvmAnalyzedFrames {
    val oldMaxLocals = method.maxLocals
    val oldMaxStack = method.maxStack
    return try {
        @Suppress("UNCHECKED_CAST")
        val frames = Analyzer(interpreter).analyzeAndComputeMaxs(owner, method) as Array<Frame<BasicValue>?>
        JvmAnalyzedFrames(frames, method.maxLocals, method.maxStack)
    } finally {
        method.maxLocals = oldMaxLocals
        method.maxStack = oldMaxStack
    }
}