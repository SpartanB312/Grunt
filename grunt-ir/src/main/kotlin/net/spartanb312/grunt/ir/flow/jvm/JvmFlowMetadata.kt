package net.spartanb312.grunt.ir.flow.jvm

import net.spartanb312.grunt.ir.flow.core.FlowBlock
import net.spartanb312.grunt.ir.flow.core.FlowIdAllocator
import net.spartanb312.grunt.ir.flow.core.FlowMethod
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodNode

class JvmFlowImportContext(
    val ids: FlowIdAllocator = FlowIdAllocator()
) {
    val metadata = JvmFlowMetadata()
}

class JvmFlowMetadata {
    var access: Int = 0
    var signature: String? = null
    val exceptions = mutableListOf<String>()
    var maxLocals: Int = 0
    var maxStack: Int = 0

    val blockStarts = linkedMapOf<FlowBlock, Int>()
    val blockEnds = linkedMapOf<FlowBlock, Int>()
    val originalInstructions = linkedMapOf<FlowBlock, List<AbstractInsnNode>>()

    fun capture(method: MethodNode) {
        access = method.access
        signature = method.signature
        exceptions.clear()
        exceptions += method.exceptions.orEmpty()
        maxLocals = method.maxLocals
        maxStack = method.maxStack
        blockStarts.clear()
        blockEnds.clear()
        originalInstructions.clear()
    }
}

data class JvmFlowImportResult(
    val method: FlowMethod,
    val metadata: JvmFlowMetadata
)
