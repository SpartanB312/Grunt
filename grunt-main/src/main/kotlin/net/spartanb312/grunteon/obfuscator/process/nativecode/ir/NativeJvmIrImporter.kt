package net.spartanb312.grunteon.obfuscator.process.nativecode.ir

import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAImporter
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode

internal object NativeJvmIrImporter {

    fun import(ownerInternalName: String, methodNode: MethodNode): NativeJvmMethodIr {
        val rawInstructions = methodNode.instructions?.toArray()?.toList().orEmpty()
        val tryCatchRegions = methodNode.tryCatchBlocks.orEmpty().mapIndexed { index, tryCatch ->
            NativeJvmTryCatchRegion(
                id = index,
                priority = index,
                startIndex = nextExecutableIndex(rawInstructions, tryCatch.start),
                endIndex = nextExecutableIndex(rawInstructions, tryCatch.end),
                handlerIndex = nextExecutableIndex(rawInstructions, tryCatch.handler),
                caughtType = tryCatch.type
            )
        }
        val activeRegionIdsByInstruction = activeTryCatchRegions(rawInstructions, methodNode.tryCatchBlocks.orEmpty())
        val nativeInstructions = rawInstructions.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode < 0) {
                null
            } else {
                NativeJvmInstruction(
                    instructionIndex = index,
                    opcode = instruction.opcode,
                    nodeType = instruction.type,
                    node = instruction,
                    activeTryCatchRegionIds = activeRegionIdsByInstruction[index].orEmpty()
                )
            }
        }

        val ssa = runCatching {
            JvmSSAImporter().import(ownerInternalName, methodNode)
        }.fold(
            onSuccess = { NativeJvmSsaOverlay(it.function, it.metadata) to null },
            onFailure = { null to (it.message ?: it::class.qualifiedName ?: "unknown SSA import failure") }
        )

        return NativeJvmMethodIr(
            ownerInternalName = ownerInternalName,
            name = methodNode.name,
            desc = methodNode.desc,
            access = methodNode.access,
            maxStack = methodNode.maxStack,
            maxLocals = methodNode.maxLocals,
            instructions = nativeInstructions,
            tryCatchRegions = tryCatchRegions,
            ssaOverlay = ssa.first,
            ssaImportError = ssa.second
        )
    }

    private fun activeTryCatchRegions(
        instructions: List<AbstractInsnNode>,
        tryCatchBlocks: List<TryCatchBlockNode>
    ): Map<Int, List<Int>> {
        if (tryCatchBlocks.isEmpty()) return emptyMap()

        val starting = tryCatchBlocks
            .mapIndexed { index, tryCatch -> tryCatch.start to index }
            .groupBy({ it.first }, { it.second })
        val ending = tryCatchBlocks
            .mapIndexed { index, tryCatch -> tryCatch.end to index }
            .groupBy({ it.first }, { it.second })
        val active = linkedSetOf<Int>()
        val result = linkedMapOf<Int, List<Int>>()

        instructions.forEachIndexed { index, instruction ->
            if (instruction is LabelNode) {
                starting[instruction].orEmpty().forEach { active += it }
                ending[instruction].orEmpty().forEach { active -= it }
            }
            if (instruction.opcode >= 0) {
                result[index] = active.toList()
            }
        }

        return result
    }

    private fun nextExecutableIndex(instructions: List<AbstractInsnNode>, label: LabelNode?): Int? {
        if (label == null) return null
        val labelIndex = instructions.indexOf(label)
        if (labelIndex < 0) return null
        for (index in labelIndex until instructions.size) {
            if (instructions[index].opcode >= 0) return index
        }
        return null
    }
}
