package net.spartanb312.grunt.ir.flow.core

import net.spartanb312.grunt.ir.flow.jvm.JvmFlowExportOptions
import net.spartanb312.grunt.ir.flow.jvm.JvmFlowExporter
import net.spartanb312.grunt.ir.test.loadTestClass
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnNode
import kotlin.test.*

class FlowGraphPerformanceTest {
    @Test
    fun verifierAndExporterTraverseTheEdgeStorageOnlyLinearly() {
        val blocks = MutableList(512) { FlowBlock(FlowBlockId(it), jump = FlowGotoJump()) }
        blocks.last().jump = FlowReturnJump()
        val edges = CountingEdges(blocks.zipWithNext().mapIndexed { index, (from, to) ->
            FlowEdge(FlowEdgeId(index), from, FlowPort.Next, to)
        }.toMutableList())
        val method = FlowMethod("example/FlowPerf", "chain", "()V", blocks, edges)
        edges.reads = 0
        FlowVerifier.verify(method).requireValid()
        assertTrue(edges.reads <= 4 * edges.size, "edge reads: " + edges.reads)
        edges.reads = 0
        val exported = JvmFlowExporter(options = JvmFlowExportOptions(access = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC))
            .export(method)
        assertTrue(edges.reads <= 8 * edges.size, "edge reads: " + edges.reads)
        loadTestClass("example/FlowPerf", exported).getMethod("chain").invoke(null)
    }

    @Test
    fun largeSwitchAndInvalidPortsKeepTheirContracts() {
        val source = FlowBlock(FlowBlockId(0), bodyExitFrame = FlowFrame(stack = listOf(FlowFrameValue.Int)))
        val target = FlowBlock(FlowBlockId(1), jump = FlowReturnJump())
        source.body.append(InsnNode(Opcodes.ICONST_0))
        source.jump = FlowSwitchJump(FlowJumpInput.StackConsumed(listOf(FlowFrameValue.Int)), (0 until 4096).toList())
        val method = FlowMethod("example/FlowPerf", "select", "()V", mutableListOf(source, target))
        source.jump.ports.forEachIndexed { index, port -> method.addEdge(FlowEdge(FlowEdgeId(index), source, port, target)) }
        FlowVerifier.verify(method).requireValid()
        method.edges.removeAt(0)
        method.addEdge(FlowEdge(FlowEdgeId(5000), source, FlowPort.Case(1), target))
        method.addEdge(FlowEdge(FlowEdgeId(5001), source, FlowPort.Named("unknown"), target))
        assertEquals(listOf(
            "Jump port case<0> has no edge",
            "Jump port case<1> has 2 edges",
            "Edge uses port unknown, but block jump does not expose it"
        ), FlowVerifier.verify(method).issues.map { it.message })
    }

    @Test
    fun snapshotsObserveMutationsAndUncheckedExportStillChoosesFirstDuplicate() {
        val source = FlowBlock(FlowBlockId(0), jump = FlowGotoJump())
        fun target(id: Int, opcode: Int) = FlowBlock(
            FlowBlockId(id), jump = FlowReturnJump(FlowJumpInput.StackConsumed(listOf(FlowFrameValue.Int))),
            bodyExitFrame = FlowFrame(stack = listOf(FlowFrameValue.Int))
        ).also { it.body.append(InsnNode(opcode)) }
        val first = target(1, Opcodes.ICONST_1)
        val second = target(2, Opcodes.ICONST_2)
        val method = FlowMethod("example/FlowPerf", "pick", "()I", mutableListOf(source, first, second))
        val edge = method.addEdge(FlowEdge(FlowEdgeId(0), source, FlowPort.Next, first))
        method.addEdge(FlowEdge(FlowEdgeId(1), source, FlowPort.Next, second))
        assertFalse(FlowVerifier.verify(method).isValid)
        val exporter = JvmFlowExporter(options = JvmFlowExportOptions(
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, verifyBeforeExport = false
        ))
        fun result(): Any? = loadTestClass("example/FlowPerf", exporter.export(method)).getMethod("pick").invoke(null)
        assertEquals(1, result())
        edge.to = second
        assertEquals(2, result())
        method.edges.removeAt(1)
        FlowVerifier.verify(method).requireValid()
    }

    @Test
    fun regionIndicesPreserveGraphEdgeOrderAndExceptionMultiplicity() {
        val blocks = MutableList(24) { FlowBlock(FlowBlockId(it), jump = FlowGotoJump()) }
        blocks.last().jump = FlowReturnJump()
        val edges = CountingEdges(blocks.zipWithNext().mapIndexed { index, (from, to) ->
            FlowEdge(FlowEdgeId(index), from, FlowPort.Next, to)
        }.reversed().toMutableList())
        val method = FlowMethod("example/FlowPerf", "regions", "()V", blocks, edges)
        method.edges.add(FlowEdge(FlowEdgeId(100), blocks[3], FlowPort.Named("self"), blocks[3]))
        val exception = FlowExceptionRegion(mutableSetOf(blocks[8], blocks[9]), blocks.last())
        method.exceptionRegions.add(exception)
        method.exceptionRegions.add(exception)
        for (allowExceptions in listOf(false, true)) {
            edges.reads = 0
            val regions = method.planRegions(FlowRegionPlanOptions(
                minBlocks = 2, maxBlocks = 4, includeMethodEntry = true, allowExceptionBlocks = allowExceptions
            ))
            assertTrue(edges.reads <= 2 * edges.size)
            for (region in regions) {
                assertEquals(method.edges.filter { it.from in region.blocks && it.to in region.blocks }, region.internalEdges)
                assertEquals(method.edges.filter { it.from in region.blocks && it.to !in region.blocks }, region.exitEdges)
                assertEquals(method.edges.filter { it.from !in region.blocks && it.to == region.entry }, region.entryEdges)
                assertEquals(method.exceptionRegions.filter {
                    it.handler in region.blocks || it.protectedBlocks.any(region.blocks::contains)
                }, region.exceptionRegions)
                if (!allowExceptions) assertTrue(region.blocks.none { it in exception.protectedBlocks || it == exception.handler })
            }
        }
    }

    private class CountingEdges(private val delegate: MutableList<FlowEdge>) : AbstractMutableList<FlowEdge>() {
        var reads = 0
        override val size get() = delegate.size
        override fun get(index: Int): FlowEdge { reads++; return delegate[index] }
        override fun set(index: Int, element: FlowEdge): FlowEdge = delegate.set(index, element)
        override fun add(index: Int, element: FlowEdge) = delegate.add(index, element)
        override fun removeAt(index: Int): FlowEdge = delegate.removeAt(index)
    }
}