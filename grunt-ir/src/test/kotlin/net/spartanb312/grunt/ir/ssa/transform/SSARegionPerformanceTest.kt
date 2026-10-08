package net.spartanb312.grunt.ir.ssa.transform

import net.spartanb312.grunt.ir.ssa.core.*
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAExporter
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAImporter
import net.spartanb312.grunt.ir.test.loadTestClass
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import kotlin.random.Random
import kotlin.test.*

class SSARegionPerformanceTest {
    @Test
    fun sparseLiveOutsMatchWholeFunctionFixedPointAfterInvalidation() {
        // The oracle intentionally uses arbitrary cyclic graphs, including unreachable components.
        // SSA dominance is irrelevant to this dataflow equivalence check.
        repeat(24) { seed ->
            val random = Random(seed)
            val ids = SSAIdAllocator()
            val parameter = SSAParameter(ids.valueId(), 0, SSAI32Type)
            val blocks = MutableList(12) { SSABlock(ids.blockId()) }
            val values = blocks.map { SSAInstructionResult(ids.valueId(), SSAI32Type) }
            blocks.forEachIndexed { index, block ->
                block.append(SSAUnaryInstruction(values[index], SSAUnaryOp.Neg, SSAIntLiteral(index.toLong())))
                block.append(SSABinaryInstruction(
                    SSAInstructionResult(ids.valueId(), SSAI32Type), SSABinaryOp.Add,
                    values[random.nextInt(values.size)], if (index % 3 == 0) parameter else values[random.nextInt(values.size)]
                ))
                block.terminator = SSABranchTerminator(
                    SSABoolLiteral(true), SSASuccessor(blocks[random.nextInt(blocks.size)]),
                    SSASuccessor(blocks[random.nextInt(blocks.size)])
                )
            }
            val function = SSAFunction(
                SSAFunctionSymbol(ids.symbolId(), "oracle", listOf(SSAI32Type), SSAVoidType),
                mutableListOf(parameter), blocks, blocks.first(),
                mutableListOf(SSAExceptionRegion(mutableSetOf(blocks[2], blocks[3]), blocks.last()))
            )
            val analysis = SSARegionAnalysis(function)
            fun compare(round: Int) {
                for (start in listOf(0, 3, 7)) {
                    val region = blocks.subList(start, start + 2).toSet()
                    val definitions = definitions(region)
                    val actual = analysis.liveOutValues(region, definitions)
                        .mapValues { (_, uses) -> uses.mapTo(mutableSetOf()) { it.id } }
                    assertEquals(legacyLiveOuts(function, region, definitions.keys), actual, "seed=" + seed + ", round=" + round)
                }
            }
            compare(0)
            repeat(8) { round ->
                val block = blocks[random.nextInt(blocks.size)]
                if (round % 2 == 0) {
                    block.terminator = SSAJumpTerminator(SSASuccessor(blocks[random.nextInt(blocks.size)]))
                } else {
                    block.instructions.removeAt(block.instructions.lastIndex)
                    block.append(SSAUnaryInstruction(
                        SSAInstructionResult(ids.valueId(), SSAI32Type), SSAUnaryOp.Neg,
                        if (round % 3 == 0) parameter else values[random.nextInt(values.size)]
                    ))
                }
                analysis.refresh(block)
                compare(round + 1)
            }
            val added = SSABlock(ids.blockId())
            added.append(SSAUnaryInstruction(SSAInstructionResult(ids.valueId(), SSAI32Type), SSAUnaryOp.Neg, values[0]))
            added.terminator = SSAJumpTerminator(SSASuccessor(blocks[7]))
            blocks[6].terminator = SSAJumpTerminator(SSASuccessor(added))
            analysis.insertedBefore(blocks[7], listOf(added))
            blocks.add(7, added)
            analysis.refresh(added)
            analysis.refresh(blocks[6])
            compare(10)
        }
    }

    @Test
    fun liveOutQueriesDoNotReadUnrelatedInstructionListsAgain() {
        val ids = SSAIdAllocator()
        val lists = List(512) { CountingInstructions() }
        val values = List(lists.size) { SSAInstructionResult(ids.valueId(), SSAI32Type) }
        val blocks = lists.mapIndexed { index, list ->
            list.add(SSAUnaryInstruction(values[index], SSAUnaryOp.Neg, SSAIntLiteral(1)))
            SSABlock(ids.blockId(), instructions = list, terminator = SSAReturnTerminator())
        }.toMutableList()
        val function = SSAFunction(SSAFunctionSymbol(ids.symbolId(), "independent", emptyList(), SSAVoidType), mutableListOf(), blocks, blocks[0])
        val analysis = SSARegionAnalysis(function)
        lists.forEach { it.reads = 0 }
        blocks.forEachIndexed { index, block ->
            assertTrue(analysis.liveOutValues(setOf(block), mapOf(values[index].id to values[index])).isEmpty())
        }
        assertEquals(0, lists.sumOf { it.reads })
    }

    @Test
    fun manyRegionsRepairTransitiveLiveOutsAndExecute() {
        val ids = SSAIdAllocator()
        val parameter = SSAParameter(ids.valueId(), 0, SSAI32Type)
        val blocks = MutableList(12) { SSABlock(ids.blockId()) }
        val values = blocks.map { SSAInstructionResult(ids.valueId(), SSAI32Type) }
        blocks.forEachIndexed { index, block ->
            block.append(SSABinaryInstruction(values[index], SSABinaryOp.Add,
                if (index == 0) parameter else values[index - 1], SSAIntLiteral((index + 1).toLong())))
            if (index + 1 < blocks.size) block.terminator = SSAJumpTerminator(SSASuccessor(blocks[index + 1]))
        }
        val partial = SSAInstructionResult(ids.valueId(), SSAI32Type)
        val result = SSAInstructionResult(ids.valueId(), SSAI32Type)
        blocks.last().append(SSABinaryInstruction(partial, SSABinaryOp.Add, values[0], values[5]))
        blocks.last().append(SSABinaryInstruction(result, SSABinaryOp.Add, partial, values[11]))
        blocks.last().terminator = SSAReturnTerminator(result)
        val original = blocks.toList()
        val function = SSAFunction(
            SSAFunctionSymbol(ids.symbolId(), Owner + ".chain(I)I", listOf(SSAI32Type), SSAI32Type),
            mutableListOf(parameter), blocks, blocks.first()
        )
        SSAVerifier.verify(function).requireValid()
        val flattened = SSARegionControlFlowFlattener().flatten(function)
        assertEquals(6, flattened.flattenedRegions)
        assertEquals(original, function.blocks.filter { it in original })
        val method = compiled(function, "chain")
        for (input in listOf(-100, -1, 0, 1, 123)) assertEquals(3 * input + 100, method.invoke(null, input))
    }

    @Test
    fun regionalLoopBackedgesAndLiveInsExecuteAfterMultipleRefreshes() {
        val ids = SSAIdAllocator()
        val n = SSAParameter(ids.valueId(), 0, SSAI32Type)
        val entry = SSABlock(ids.blockId())
        val header = SSABlock(ids.blockId())
        val bodyA = SSABlock(ids.blockId())
        val bodyB = SSABlock(ids.blockId())
        val step = SSABlock(ids.blockId())
        val exitA = SSABlock(ids.blockId())
        val exitB = SSABlock(ids.blockId())
        fun result() = SSAInstructionResult(ids.valueId(), SSAI32Type)
        val base = result()
        entry.append(SSABinaryInstruction(base, SSABinaryOp.Add, n, SSAIntLiteral(1)))
        val index = SSABlockArg(ids.valueId(), 0, SSAI32Type)
        val total = SSABlockArg(ids.valueId(), 1, SSAI32Type)
        header.args += listOf(index, total)
        entry.terminator = SSAJumpTerminator(SSASuccessor(header, listOf(n, SSAIntLiteral(0))))
        val condition = SSAInstructionResult(ids.valueId(), SSABoolType)
        header.append(SSACompareInstruction(condition, SSAComparePredicate.Gt, index, SSAIntLiteral(0)))
        header.terminator = SSABranchTerminator(condition, SSASuccessor(bodyA), SSASuccessor(exitA))
        val increment = result()
        bodyA.append(SSABinaryInstruction(increment, SSABinaryOp.Mul, base, SSAIntLiteral(2)))
        bodyA.terminator = SSAJumpTerminator(SSASuccessor(bodyB))
        val nextTotal = result()
        bodyB.append(SSABinaryInstruction(nextTotal, SSABinaryOp.Add, total, increment))
        bodyB.terminator = SSAJumpTerminator(SSASuccessor(step))
        val nextIndex = result()
        step.append(SSABinaryInstruction(nextIndex, SSABinaryOp.Sub, index, SSAIntLiteral(1)))
        step.terminator = SSAJumpTerminator(SSASuccessor(header, listOf(nextIndex, nextTotal)))
        val answer = result()
        exitA.append(SSABinaryInstruction(answer, SSABinaryOp.Add, total, base))
        exitA.terminator = SSAJumpTerminator(SSASuccessor(exitB))
        exitB.terminator = SSAReturnTerminator(answer)
        val function = SSAFunction(
            SSAFunctionSymbol(ids.symbolId(), Owner + ".loop(I)I", listOf(SSAI32Type), SSAI32Type),
            mutableListOf(n), mutableListOf(entry, header, bodyA, bodyB, step, exitA, exitB), entry
        )
        SSAVerifier.verify(function).requireValid()
        val flattened = SSARegionControlFlowFlattener().flatten(function)
        assertEquals(3, flattened.flattenedRegions)
        val method = compiled(function, "loop")
        for (input in listOf(-2, -1, 0, 1, 5, 11)) {
            assertEquals((2 * input.coerceAtLeast(0) + 1) * (input + 1), method.invoke(null, input))
        }
    }

    @Test
    fun exceptionBoundariesArePreservedWhenProtectedBlocksAreSkipped() {
        val start = LabelNode()
        val end = LabelNode()
        val handler = LabelNode()
        val outside = LabelNode()
        val tail = LabelNode()
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "guard", "(I)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(VarInsnNode(Opcodes.ISTORE, 1))
            instructions.add(JumpInsnNode(Opcodes.GOTO, start))
            instructions.add(start)
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(InsnNode(Opcodes.IDIV))
            instructions.add(VarInsnNode(Opcodes.ISTORE, 1))
            instructions.add(JumpInsnNode(Opcodes.GOTO, outside))
            instructions.add(end)
            instructions.add(handler)
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.ICONST_M1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(outside)
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.ICONST_2))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(VarInsnNode(Opcodes.ISTORE, 1))
            instructions.add(JumpInsnNode(Opcodes.GOTO, tail))
            instructions.add(tail)
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            tryCatchBlocks.add(TryCatchBlockNode(start, end, handler, "java/lang/ArithmeticException"))
            maxLocals = 2
            maxStack = 2
        }
        val imported = JvmSSAImporter().import(Owner, method)
        val protected = imported.function.exceptionRegions.single().protectedBlocks.toSet()
        assertTrue(SSARegionControlFlowFlattener(SSARegionControlFlowFlattenOptions(skipExceptionRegions = true)).flatten(imported.function).changed)
        assertEquals(protected, imported.function.exceptionRegions.single().protectedBlocks)
        SSAVerifier.verify(imported.function).requireValid()
        val exported = JvmSSAExporter(imported.metadata).export(imported.function)
        Analyzer(BasicInterpreter()).analyze(Owner, exported)
        val reflected = loadTestClass(Owner, exported).getMethod("guard", Int::class.javaPrimitiveType)
        for (input in listOf(-2, -1, 0, 1, 5)) assertEquals(if (input == 0) -1 else (input + 1) / input + 2, reflected.invoke(null, input))
    }

    @Test
    fun rejectedRegionsAndUnrelatedTerminatorsRemainUntouched() {
        val ids = SSAIdAllocator()
        val entry = SSABlock(ids.blockId())
        val target = SSABlock(ids.blockId())
        repeat(80) { target.args += SSABlockArg(ids.valueId(), it, SSAI32Type) }
        entry.terminator = SSAJumpTerminator(SSASuccessor(target, List(80) { SSAIntLiteral(0) }))
        target.terminator = SSAReturnTerminator()
        val function = SSAFunction(SSAFunctionSymbol(ids.symbolId(), "reject", emptyList(), SSAVoidType), mutableListOf(), mutableListOf(entry, target), entry)
        val terminator = entry.terminator
        assertFalse(SSARegionControlFlowFlattener().flatten(function).changed)
        assertSame(terminator, entry.terminator)
        assertEquals(80, target.args.size)
        target.args.clear()
        entry.terminator = SSAJumpTerminator(SSASuccessor(target))
        val unrelated = SSABlock(ids.blockId(), terminator = SSAReturnTerminator())
        val unrelatedTerminator = unrelated.terminator
        function.blocks += unrelated
        assertTrue(SSARegionControlFlowFlattener().flatten(function).changed)
        assertSame(unrelatedTerminator, unrelated.terminator)
        SSAVerifier.verify(function).requireValid()
    }

    @Test
    fun plannerEdgesMatchTheOriginalOrderIncludingSelfLoopsAndExceptions() {
        val ids = SSAIdAllocator()
        val blocks = MutableList(48) { SSABlock(ids.blockId()) }
        blocks.zipWithNext().forEach { (from, to) -> from.terminator = SSAJumpTerminator(SSASuccessor(to)) }
        blocks[3].terminator = SSABranchTerminator(SSABoolLiteral(false), SSASuccessor(blocks[3]), SSASuccessor(blocks[4]))
        blocks.last().terminator = SSAReturnTerminator()
        val function = SSAFunction(
            SSAFunctionSymbol(ids.symbolId(), "regions", emptyList(), SSAVoidType), mutableListOf(), blocks, blocks[0],
            mutableListOf(SSAExceptionRegion(mutableSetOf(blocks[8], blocks[9]), blocks.last()))
        )
        val edges = function.normalEdges()
        for (allowExceptions in listOf(false, true)) for (preferSmall in listOf(false, true)) {
            val regions = function.planRegions(SSARegionPlanOptions(
                minBlocks = 2, maxBlocks = 4, includeFunctionEntry = true,
                allowExceptionBlocks = allowExceptions, preferSmallRegions = preferSmall
            ))
            for (region in regions) {
                assertEquals(edges.filter { it.from in region.blocks && it.to in region.blocks }, region.internalEdges)
                assertEquals(edges.filter { it.from in region.blocks && it.to !in region.blocks }, region.exitEdges)
                assertEquals(edges.filter { it.from !in region.blocks && it.to == region.entry }, region.entryEdges)
            }
        }
    }

    private fun compiled(function: SSAFunction, name: String): java.lang.reflect.Method {
        SSAVerifier.verify(function).requireValid()
        val exported = JvmSSAExporter().export(function)
        Analyzer(BasicInterpreter()).analyze(Owner, exported)
        return loadTestClass(Owner, exported).getMethod(name, Int::class.javaPrimitiveType)
    }

    private fun definitions(blocks: Set<SSABlock>): Map<SSAValueId, SSAStructure> = buildMap {
        for (block in blocks) {
            block.args.forEach { put(it.id, it) }
            block.instructions.forEach { it.result?.let { value -> put(value.id, value) } }
        }
    }

    private fun legacyLiveOuts(function: SSAFunction, region: Set<SSABlock>, ids: Set<SSAValueId>): Map<SSABlock, Set<SSAValueId>> {
        val parameters = function.parameters.mapTo(mutableSetOf()) { it.id }
        val defs = function.blocks.associateWith { definitions(setOf(it)).keys }
        val uses = function.blocks.associateWith { block ->
            (block.instructions.flatMap { it.operands } + block.terminator.operands)
                .filterIsInstance<SSAStructure>().map { it.id }
                .filterTo(mutableSetOf()) { it !in parameters && it !in defs.getValue(block) }
        }
        val live = function.blocks.associateWith { mutableSetOf<SSAValueId>() }
        var changed: Boolean
        do {
            changed = false
            for (block in function.blocks.asReversed()) {
                val next = uses.getValue(block).toMutableSet()
                for (successor in block.terminator.successors) {
                    next += live[successor.block].orEmpty().filter { it !in parameters && it !in defs.getValue(block) }
                }
                if (next != live.getValue(block)) {
                    live.getValue(block).clear()
                    live.getValue(block).addAll(next)
                    changed = true
                }
            }
        } while (changed)
        return live.filterKeys { it !in region }.mapValues { (_, values) -> values.intersect(ids) }.filterValues { it.isNotEmpty() }
    }

    private class CountingInstructions : AbstractMutableList<SSAInstruction>() {
        private val delegate = mutableListOf<SSAInstruction>()
        var reads = 0
        override val size get() = delegate.size
        override fun get(index: Int): SSAInstruction { reads++; return delegate[index] }
        override fun set(index: Int, element: SSAInstruction): SSAInstruction = delegate.set(index, element)
        override fun add(index: Int, element: SSAInstruction) = delegate.add(index, element)
        override fun removeAt(index: Int): SSAInstruction = delegate.removeAt(index)
    }

    private companion object {
        const val Owner = "example/RegionPerf"
    }
}