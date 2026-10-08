package net.spartanb312.grunt.ir.ssa.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SSADominancePerformanceTest {
    @Test
    fun naturalReversedAndShuffledChainsMatchLegacyAcrossBitSetWords() {
        val graph = Graph(129)
        for (index in 0 until graph.blocks.lastIndex) graph.jump(index, index + 1)
        graph.probeAllPairs()

        assertLayoutsMatchLegacy(graph)
    }

    @Test
    fun diamondMatchesLegacyForEveryDefinitionUsePair() {
        val graph = Graph(5)
        graph.branch(0, 1, 2)
        graph.jump(1, 3)
        graph.jump(2, 3)
        graph.jump(3, 4)
        graph.probeAllPairs()
        // A phi input is checked at the predecessor, not at its destination.
        graph.blocks[2].terminator = SSAJumpTerminator(SSASuccessor(graph.blocks[3], listOf(graph.values[1])))

        assertLayoutsMatchLegacy(graph)
    }

    @Test
    fun naturalIrreducibleAndSelfLoopsMatchLegacy() {
        val graph = Graph(8)
        graph.branch(0, 1, 2)
        graph.branch(1, 2, 3)
        graph.branch(2, 1, 4)
        graph.jump(3, 5)
        graph.jump(4, 5)
        graph.branch(5, 6, 7)
        graph.branch(6, 5, 0)
        graph.jump(7, 7)
        graph.probeAllPairs()

        assertLayoutsMatchLegacy(graph)
    }

    @Test
    fun unreachableSccKeepsUniversalPolicyButPredecessorFreeRootsDoNot() {
        val graph = Graph(10)
        graph.jump(0, 1)
        graph.jump(1, 2)
        graph.jump(3, 4)
        graph.branch(4, 3, 5)
        graph.jump(5, 1)
        graph.jump(6, 7)
        graph.jump(7, 8)
        graph.jump(8, 7)
        graph.probeAllPairs()

        val legacy = legacyDominators(graph.function)
        for (index in 3..5) assertEquals(graph.blocks.toSet(), legacy.getValue(graph.blocks[index]))
        assertEquals(setOf(graph.blocks[6]), legacy.getValue(graph.blocks[6]))
        assertEquals(setOf(graph.blocks[6], graph.blocks[7]), legacy.getValue(graph.blocks[7]))
        assertEquals(setOf(graph.blocks[9]), legacy.getValue(graph.blocks[9]))
        assertLayoutsMatchLegacy(graph)
        assertTrue(SSAVerifier.verify(graph.function).issues.none { it.block in graph.blocks.subList(3, 6) })
    }

    @Test
    fun overlappingAndDuplicateExceptionEdgesParticipateInDominance() {
        val graph = Graph(6)
        graph.branch(0, 1, 2)
        graph.jump(1, 3)
        graph.jump(2, 3)
        graph.jump(3, 4)
        graph.protect(4, 1)
        graph.protect(5, 1, 2)
        graph.protect(5, 1)
        graph.protect(1, 0)
        graph.probeAllPairs()

        assertNotEquals(
            legacyDiagnostics(graph.function, includeExceptionEdges = false),
            legacyDiagnostics(graph.function)
        )
        assertLayoutsMatchLegacy(graph)
    }

    @Test
    fun seededGraphsMatchLegacyWithDisconnectedAndExceptionalEdges() {
        val random = Random(0xD011)
        repeat(24) {
            val graph = Graph(17)
            for (index in graph.blocks.indices) {
                when (random.nextInt(3)) {
                    1 -> graph.jump(index, random.nextInt(graph.blocks.size))
                    2 -> graph.branch(index, random.nextInt(graph.blocks.size), random.nextInt(graph.blocks.size))
                }
            }
            repeat(4) {
                graph.protect(
                    random.nextInt(graph.blocks.size),
                    random.nextInt(graph.blocks.size),
                    random.nextInt(graph.blocks.size)
                )
            }
            graph.probeAllPairs()
            assertLayoutsMatchLegacy(graph)
        }
    }

    @Test
    fun mutationsBetweenVerificationsRebuildTheDominanceSnapshot() {
        val graph = Graph(4)
        for (index in 0 until graph.blocks.lastIndex) graph.jump(index, index + 1)
        graph.probeAllPairs()
        val initial = assertMatchesLegacy(graph.function)

        graph.protect(3, 0)
        assertNotEquals(initial, assertMatchesLegacy(graph.function))
        graph.function.exceptionRegions.clear()
        assertEquals(initial, assertMatchesLegacy(graph.function))
        graph.branch(0, 1, 3)
        assertNotEquals(initial, assertMatchesLegacy(graph.function))
        graph.function.entry = graph.blocks[1]
        assertMatchesLegacy(graph.function)
    }

    @Test
    fun localDefinitionOrderParametersAndUndefinedValuesKeepDiagnostics() {
        val graph = Graph(1)
        val undefined = SSAInstructionResult(SSAValueId(Int.MAX_VALUE), SSAI32Type)
        graph.probe(0, listOf(graph.values[0], graph.args[0], graph.condition, undefined))
        val block = graph.blocks[0]
        block.instructions.add(0, block.instructions.removeAt(block.instructions.lastIndex))

        val issues = assertMatchesLegacy(graph.function)
        assertEquals(
            listOf(
                "${block.id}: Use of ${graph.values[0].id} is not dominated by its definition",
                "${block.id}: Use of undefined SSA value ${undefined.id}"
            ),
            issues.map { it.toString() }
        )
    }

    @Test
    fun missingEntryAndForeignEdgesKeepStructuralDiagnostics() {
        val graph = Graph(3)
        graph.jump(0, 1)
        graph.jump(1, 2)
        graph.probeAllPairs()
        val foreign = SSABlock(SSABlockId(Int.MAX_VALUE))
        graph.function.entry = foreign
        graph.blocks[2].terminator = SSAJumpTerminator(SSASuccessor(foreign))
        graph.function.exceptionRegions += SSAExceptionRegion(linkedSetOf(foreign), graph.blocks[2])
        graph.function.exceptionRegions += SSAExceptionRegion(linkedSetOf(graph.blocks[1]), foreign)

        val expected = listOf(SSAVerificationIssue("Entry block ${foreign.id} is not part of the function")) +
                legacyDiagnostics(graph.function) + listOf(
            SSAVerificationIssue("Successor ${foreign.id} is not part of the function", graph.blocks[2]),
            SSAVerificationIssue("Protected block ${foreign.id} is not part of the function"),
            SSAVerificationIssue("Exception handler ${foreign.id} is not part of the function")
        )
        assertEquals(expected, SSAVerifier.verify(graph.function).issues)

        graph.function.blocks.clear()
        graph.function.exceptionRegions.clear()
        assertEquals(
            listOf(SSAVerificationIssue("Entry block ${foreign.id} is not part of the function")),
            SSAVerifier.verify(graph.function).issues
        )
    }

    @Test
    fun duplicateBlockIdsDoNotCollapseDistinctBlockObjects() {
        val graph = Graph(3) { if (it == 2) 100 else 100 + it }
        graph.jump(0, 1)
        graph.jump(1, 2)
        graph.probeAllPairs()

        val expected = listOf(SSAVerificationIssue("Duplicate block id b100", graph.blocks[2])) +
                legacyDiagnostics(graph.function)
        assertEquals(expected, SSAVerifier.verify(graph.function).issues)
    }

    @Test
    fun largeReverseChainVerifiesWithoutRecursiveTraversalOrTimingThresholds() {
        // Do not run the cubic legacy solver here: the small all-pairs tests are the oracle.
        val graph = Graph(4096)
        for (index in graph.blocks.indices) {
            if (index < graph.blocks.lastIndex) graph.jump(index, index + 1)
            val previous = (index - 1).coerceAtLeast(0)
            graph.probe(index, listOf(graph.args[0], graph.values[previous], graph.values[index]))
        }
        graph.function.blocks.reverse()
        SSAVerifier.verify(graph.function).requireValid()

        graph.probe(0, listOf(graph.values.last()))
        assertEquals(
            listOf(
                SSAVerificationIssue(
                    "Use of ${graph.values.last().id} is not dominated by its definition",
                    graph.blocks[0]
                )
            ),
            SSAVerifier.verify(graph.function).issues
        )
    }

    private fun assertLayoutsMatchLegacy(graph: Graph) {
        val natural = graph.blocks
        val layouts = listOf(natural, natural.reversed(), natural.shuffled(Random(0x5AA)))
        var canonical: List<String>? = null
        for ((index, layout) in layouts.withIndex()) {
            graph.function.blocks.clear()
            graph.function.blocks.addAll(layout)
            val diagnostics = assertMatchesLegacy(graph.function, "layout $index").map { it.toString() }.sorted()
            if (canonical == null) canonical = diagnostics else assertEquals(canonical, diagnostics, "layout $index")
        }
    }

    private fun assertMatchesLegacy(function: SSAFunction, message: String = ""): List<SSAVerificationIssue> {
        val expected = legacyDiagnostics(function)
        val actual = SSAVerifier.verify(function).issues
        assertEquals(expected, actual, message)
        return actual
    }

    /** Independent copy of the pre-optimization set solver, intentionally retaining its update order. */
    private fun legacyDominators(
        function: SSAFunction,
        includeExceptionEdges: Boolean = true
    ): Map<SSABlock, Set<SSABlock>> {
        val blocks = function.blocks.toSet()
        if (blocks.isEmpty()) return emptyMap()
        val predecessors = function.predecessors(includeExceptionEdges)
        val dominators = mutableMapOf<SSABlock, MutableSet<SSABlock>>()
        for (block in blocks) {
            dominators[block] = if (block == function.entry) mutableSetOf(block) else blocks.toMutableSet()
        }
        var changed: Boolean
        do {
            changed = false
            for (block in blocks) {
                if (block == function.entry) continue
                val preds = predecessors[block].orEmpty().filter { it in blocks }
                val next = if (preds.isEmpty()) {
                    mutableSetOf(block)
                } else {
                    preds.map { dominators.getValue(it) }
                        .reduce { acc, set -> acc.intersect(set).toMutableSet() }
                        .toMutableSet()
                        .also { it += block }
                }
                val current = dominators.getValue(block)
                if (current != next) {
                    current.clear()
                    current += next
                    changed = true
                }
            }
        } while (changed)
        return dominators
    }

    /** Fixtures are well-typed; only use/definition diagnostics are expected unless explicitly added. */
    private fun legacyDiagnostics(
        function: SSAFunction,
        includeExceptionEdges: Boolean = true
    ): List<SSAVerificationIssue> {
        val dominators = legacyDominators(function, includeExceptionEdges)
        val definitions = mutableMapOf<SSAValueId, LegacyDefinition>()
        for (parameter in function.parameters) definitions[parameter.id] = LegacyDefinition(null, -1)
        for (block in function.blocks) {
            for (arg in block.args) definitions.putIfAbsent(arg.id, LegacyDefinition(block, -1))
            block.instructions.forEachIndexed { index, instruction ->
                instruction.result?.let { definitions.putIfAbsent(it.id, LegacyDefinition(block, index)) }
            }
        }
        val issues = mutableListOf<SSAVerificationIssue>()
        fun verifyUse(value: SSAValue, block: SSABlock, index: Int) {
            if (value !is SSAStructure) return
            val definition = definitions[value.id]
            if (definition == null) {
                issues += SSAVerificationIssue("Use of undefined SSA value ${value.id}", block)
                return
            }
            val valid = when (definition.block) {
                null -> true
                block -> definition.index < index
                else -> definition.block in dominators.getValue(block)
            }
            if (!valid) {
                issues += SSAVerificationIssue("Use of ${value.id} is not dominated by its definition", block)
            }
        }
        for (block in function.blocks) {
            block.instructions.forEachIndexed { index, instruction ->
                for (operand in instruction.operands) verifyUse(operand, block, index)
            }
            for (operand in block.terminator.operands) verifyUse(operand, block, block.instructions.size)
        }
        return issues
    }

    private data class LegacyDefinition(val block: SSABlock?, val index: Int)

    private class Graph(size: Int, blockId: (Int) -> Int = { 1_000_000 + it * 97 }) {
        private val ids = SSAIdAllocator()
        val blocks = List(size) { SSABlock(SSABlockId(blockId(it))) }
        val condition = SSAParameter(ids.valueId(), 0, SSABoolType)
        val args = blocks.map { block ->
            SSABlockArg(ids.valueId(), 0, SSAI32Type).also { block.args += it }
        }
        val values = blocks.map { block ->
            SSAInstructionResult(ids.valueId(), SSAI32Type).also {
                block.instructions += SSAUnaryInstruction(it, SSAUnaryOp.Neg, SSAIntLiteral(1))
            }
        }
        val function = SSAFunction(
            SSAFunctionSymbol(ids.symbolId(), "dominance", listOf(SSABoolType), SSAVoidType),
            mutableListOf(condition),
            blocks.toMutableList(),
            blocks.first()
        )

        fun jump(from: Int, to: Int) {
            blocks[from].terminator = SSAJumpTerminator(SSASuccessor(blocks[to], listOf(values[from])))
        }

        fun branch(from: Int, left: Int, right: Int) {
            blocks[from].terminator = SSABranchTerminator(
                condition,
                SSASuccessor(blocks[left], listOf(values[from])),
                SSASuccessor(blocks[right], listOf(args[from]))
            )
        }

        fun protect(handler: Int, vararg protectedBlocks: Int) {
            function.exceptionRegions += SSAExceptionRegion(
                protectedBlocks.mapTo(linkedSetOf()) { blocks[it] },
                blocks[handler]
            )
        }

        fun probe(block: Int, operands: List<SSAValue>) {
            blocks[block].instructions += SSAIntrinsicInstruction(
                null,
                SSAIntrinsicRef("dominanceProbe", operands.map { it.type }, SSAVoidType),
                operands
            )
        }

        fun probeAllPairs() {
            val operands = blocks.indices.flatMap { listOf(args[it], values[it]) } + condition
            for (index in blocks.indices) probe(index, operands)
        }
    }
}