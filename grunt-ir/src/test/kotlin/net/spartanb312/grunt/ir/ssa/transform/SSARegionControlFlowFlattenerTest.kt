package net.spartanb312.grunt.ir.ssa.transform

import net.spartanb312.grunt.ir.ssa.core.*
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAExporter
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAImporter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.VarInsnNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SSARegionControlFlowFlattenerTest {
    @Test
    fun doesNotFlattenSingleBlockMethod() {
        val ids = SSAIdAllocator()
        val parameter = SSAParameter(ids.valueId(), 0, SSAI32Type, "p0")
        val symbol = SSAFunctionSymbol(ids.symbolId(), "single", listOf(SSAI32Type), SSAI32Type)
        val entry = SSABlock(ids.blockId())
        entry.terminator = SSAReturnTerminator(parameter)

        val function = SSAFunction(symbol, mutableListOf(parameter), mutableListOf(entry), entry)
        val result = SSARegionControlFlowFlattener().flatten(function)

        assertFalse(result.changed)
        assertEquals(0, result.flattenedRegions)
        SSAVerifier.verify(function).requireValid()
        assertFalse(function.blocks.any { it.terminator is SSASwitchTerminator })
    }

    @Test
    fun plannerKeepsRegionsSingleEntry() {
        val ids = SSAIdAllocator()
        val symbol = SSAFunctionSymbol(ids.symbolId(), "crossJoin", emptyList(), SSAVoidType)
        val entry = SSABlock(ids.blockId())
        val left = SSABlock(ids.blockId())
        val leftMid = SSABlock(ids.blockId())
        val outside = SSABlock(ids.blockId())
        val join = SSABlock(ids.blockId())
        val tail = SSABlock(ids.blockId())

        entry.terminator = SSABranchTerminator(SSABoolLiteral(true), SSASuccessor(left), SSASuccessor(outside))
        left.terminator = SSAJumpTerminator(SSASuccessor(leftMid))
        leftMid.terminator = SSAJumpTerminator(SSASuccessor(join))
        outside.terminator = SSAJumpTerminator(SSASuccessor(join))
        join.terminator = SSAJumpTerminator(SSASuccessor(tail))
        tail.terminator = SSAReturnTerminator()

        val function = SSAFunction(symbol, mutableListOf(), mutableListOf(entry, left, leftMid, outside, join, tail), entry)
        val regions = function.planRegions(SSARegionPlanOptions(minBlocks = 2, maxBlocks = 2))

        val leftRegion = regions.single { it.entry == left }
        assertEquals(setOf(left, leftMid), leftRegion.blocks)
        assertTrue(join !in leftRegion.blocks)
        assertTrue(regions.all { region ->
            function.normalEdges().none { edge ->
                edge.from !in region.blocks && edge.to in region.blocks && edge.to != region.entry
            }
        })
    }

    @Test
    fun flattensLinearMethodIntoSmallestPossibleRegionDispatchers() {
        val ids = SSAIdAllocator()
        val parameter = SSAParameter(ids.valueId(), 0, SSAI32Type, "p0")
        val symbol = SSAFunctionSymbol(ids.symbolId(), "linear", listOf(SSAI32Type), SSAI32Type)
        val entry = SSABlock(ids.blockId())
        val b1 = SSABlock(ids.blockId())
        val b2 = SSABlock(ids.blockId())
        val b3 = SSABlock(ids.blockId())
        val b4 = SSABlock(ids.blockId())
        val b5 = SSABlock(ids.blockId())

        entry.terminator = SSAJumpTerminator(SSASuccessor(b1))
        b1.terminator = SSAJumpTerminator(SSASuccessor(b2))
        b2.terminator = SSAJumpTerminator(SSASuccessor(b3))
        b3.terminator = SSAJumpTerminator(SSASuccessor(b4))
        b4.terminator = SSAJumpTerminator(SSASuccessor(b5))
        b5.terminator = SSAReturnTerminator(parameter)

        val function = SSAFunction(symbol, mutableListOf(parameter), mutableListOf(entry, b1, b2, b3, b4, b5), entry)
        val result = SSARegionControlFlowFlattener(
            SSARegionControlFlowFlattenOptions(minRegionBlocks = 2, maxRegionBlocks = 8)
        ).flatten(function)

        assertTrue(result.changed, result.reason)
        assertEquals(3, result.flattenedRegions)
        SSAVerifier.verify(function).requireValid()
        assertTrue(function.blocks.count { it.terminator is SSASwitchTerminator } >= 3)
    }

    @Test
    fun stopsRegionGrowthAsSoonAsMinimumSizeIsReached() {
        val ids = SSAIdAllocator()
        val symbol = SSAFunctionSymbol(ids.symbolId(), "branchChains", emptyList(), SSAVoidType)
        val entry = SSABlock(ids.blockId())
        val left = SSABlock(ids.blockId())
        val leftTail = SSABlock(ids.blockId())
        val right = SSABlock(ids.blockId())
        val rightTail = SSABlock(ids.blockId())

        entry.terminator = SSABranchTerminator(SSABoolLiteral(true), SSASuccessor(left), SSASuccessor(right))
        left.terminator = SSAJumpTerminator(SSASuccessor(leftTail))
        leftTail.terminator = SSAReturnTerminator()
        right.terminator = SSAJumpTerminator(SSASuccessor(rightTail))
        rightTail.terminator = SSAReturnTerminator()

        val function = SSAFunction(symbol, mutableListOf(), mutableListOf(entry, left, leftTail, right, rightTail), entry)
        val result = SSARegionControlFlowFlattener(
            SSARegionControlFlowFlattenOptions(minRegionBlocks = 2, maxRegionBlocks = 8)
        ).flatten(function)

        assertTrue(result.changed, result.reason)
        assertEquals(2, result.flattenedRegions)
        SSAVerifier.verify(function).requireValid()
    }

    @Test
    fun triesProtectedTryCatchBlocksWhenEnabled() {
        val start = LabelNode()
        val falseLabel = LabelNode()
        val end = LabelNode()
        val handler = LabelNode()
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "guarded", "(I)I", null, null).apply {
            instructions = InsnList().apply {
                add(start)
                add(VarInsnNode(Opcodes.ILOAD, 0))
                add(JumpInsnNode(Opcodes.IFEQ, falseLabel))
                add(InsnNode(Opcodes.ICONST_1))
                add(InsnNode(Opcodes.IRETURN))
                add(falseLabel)
                add(InsnNode(Opcodes.ICONST_2))
                add(InsnNode(Opcodes.IRETURN))
                add(end)
                add(handler)
                add(InsnNode(Opcodes.POP))
                add(InsnNode(Opcodes.ICONST_M1))
                add(InsnNode(Opcodes.IRETURN))
            }
            tryCatchBlocks = mutableListOf(TryCatchBlockNode(start, end, handler, "java/lang/Throwable"))
            maxLocals = 1
            maxStack = 1
        }
        val imported = JvmSSAImporter().import("example/Test", method)
        val protectedBlocks = imported.function.exceptionRegions.single().protectedBlocks.toSet()

        val result = SSARegionControlFlowFlattener(
            SSARegionControlFlowFlattenOptions(skipExceptionRegions = false)
        ).flatten(imported.function)

        assertTrue(result.changed, result.reason)
        assertTrue(imported.function.blocks.any { block ->
            val terminator = block.terminator
            terminator is SSASwitchTerminator && terminator.successors.any { it.block in protectedBlocks }
        })
        SSAVerifier.verify(imported.function).requireValid()
        val exported = JvmSSAExporter(imported.metadata).export(imported.function)
        Analyzer(BasicInterpreter()).analyze("example/Test", exported)
    }

    @Test
    fun repairsValuesDefinedInRegionAndUsedAfterRegion() {
        val ids = SSAIdAllocator()
        val symbol = SSAFunctionSymbol(ids.symbolId(), "liveOut", emptyList(), SSAI32Type)
        val entry = SSABlock(ids.blockId())
        val producer = SSABlock(ids.blockId())
        val middle = SSABlock(ids.blockId())
        val exit = SSABlock(ids.blockId())
        val produced = SSAInstructionResult(ids.valueId(), SSAI32Type, "x")

        entry.terminator = SSAJumpTerminator(SSASuccessor(producer))
        producer.instructions += SSABinaryInstruction(produced, SSABinaryOp.Add, SSAIntLiteral(1), SSAIntLiteral(2))
        producer.terminator = SSAJumpTerminator(SSASuccessor(middle))
        middle.terminator = SSAJumpTerminator(SSASuccessor(exit))
        exit.terminator = SSAReturnTerminator(produced)

        val function = SSAFunction(symbol, mutableListOf(), mutableListOf(entry, producer, middle, exit), entry)
        val result = SSARegionControlFlowFlattener(
            SSARegionControlFlowFlattenOptions(minRegionBlocks = 2, maxRegionBlocks = 3)
        ).flatten(function)

        assertTrue(result.changed, result.reason)
        SSAVerifier.verify(function).requireValid()
        val returnTerminator = assertIs<SSAReturnTerminator>(exit.terminator)
        val exitArg = assertIs<SSABlockArg>(returnTerminator.value)
        assertTrue(exitArg.debugName?.startsWith("region0.out.") == true)
        val middleArg = middle.args.single { it.debugName?.startsWith("region0.out.") == true }
        val middleJump = assertIs<SSAJumpTerminator>(middle.terminator)
        assertTrue(middleArg in middleJump.target.args)
    }
}
