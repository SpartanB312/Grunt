package net.spartanb312.grunt.ir.flow.core

import org.objectweb.asm.Opcodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowCoreTest {
    @Test
    fun verifiesSimpleGotoGraph() {
        val ids = FlowIdAllocator()
        val entry = FlowBlock(
            id = ids.blockId(),
            jump = FlowGotoJump(),
            entryFrame = FlowFrame.Empty,
            bodyExitFrame = FlowFrame.Empty
        )
        val exit = FlowBlock(
            id = ids.blockId(),
            jump = FlowReturnJump(),
            entryFrame = FlowFrame.Empty,
            bodyExitFrame = FlowFrame.Empty
        )
        val method = FlowMethod(
            ownerInternalName = "example/Test",
            name = "run",
            desc = "()V",
            blocks = mutableListOf(entry, exit)
        )
        method.addEdge(
            FlowEdge(
                id = ids.edgeId(),
                from = entry,
                port = FlowPort.Next,
                to = exit
            )
        )

        assertTrue(FlowVerifier.verify(method).isValid)
    }

    @Test
    fun verifiesGotoDisguisedAsOpaqueIf() {
        val ids = FlowIdAllocator()
        val entry = FlowBlock(
            id = ids.blockId(),
            jump = FlowIfJump(
                opcode = Opcodes.IFEQ,
                input = FlowJumpInput.Generated(
                    code = FlowBytecodeSlice(),
                    produced = listOf(FlowFrameValue.Int),
                    guarantee = FlowPredicateGuarantee.AlwaysTrue
                )
            ),
            entryFrame = FlowFrame.Empty,
            bodyExitFrame = FlowFrame.Empty
        )
        val realTarget = FlowBlock(
            id = ids.blockId(),
            jump = FlowReturnJump(),
            entryFrame = FlowFrame.Empty,
            bodyExitFrame = FlowFrame.Empty
        )
        val bogus = FlowBlock(
            id = ids.blockId(),
            kind = FlowBlockKind.Bogus,
            jump = FlowGotoJump(FlowGotoMode.Synthetic),
            entryFrame = FlowFrame.Empty,
            bodyExitFrame = FlowFrame.Empty
        )
        val method = FlowMethod(
            ownerInternalName = "example/Test",
            name = "run",
            desc = "()V",
            blocks = mutableListOf(entry, realTarget, bogus)
        )
        method.addEdge(
            FlowEdge(
                id = ids.edgeId(),
                from = entry,
                port = FlowPort.Branch,
                to = realTarget,
                semantics = FlowEdgeSemantics.Real
            )
        )
        method.addEdge(
            FlowEdge(
                id = ids.edgeId(),
                from = entry,
                port = FlowPort.Fallthrough,
                to = bogus,
                semantics = FlowEdgeSemantics.Bogus
            )
        )
        method.addEdge(
            FlowEdge(
                id = ids.edgeId(),
                from = bogus,
                port = FlowPort.Next,
                to = realTarget,
                semantics = FlowEdgeSemantics.Synthetic
            )
        )

        assertTrue(FlowVerifier.verify(method).isValid)
    }

    @Test
    fun rejectsMissingJumpPortEdge() {
        val ids = FlowIdAllocator()
        val entry = FlowBlock(
            id = ids.blockId(),
            jump = FlowGotoJump(),
            entryFrame = FlowFrame.Empty,
            bodyExitFrame = FlowFrame.Empty
        )
        val method = FlowMethod(
            ownerInternalName = "example/Test",
            name = "run",
            desc = "()V",
            blocks = mutableListOf(entry)
        )

        val result = FlowVerifier.verify(method)

        assertFalse(result.isValid)
        assertTrue(result.issues.any { "has no edge" in it.message })
    }

    @Test
    fun rejectsFrameMismatchAcrossEdge() {
        val ids = FlowIdAllocator()
        val entry = FlowBlock(
            id = ids.blockId(),
            jump = FlowGotoJump(),
            entryFrame = FlowFrame.Empty,
            bodyExitFrame = FlowFrame.Empty
        )
        val target = FlowBlock(
            id = ids.blockId(),
            jump = FlowReturnJump(FlowJumpInput.StackConsumed(listOf(FlowFrameValue.Int))),
            entryFrame = FlowFrame(stack = listOf(FlowFrameValue.Int)),
            bodyExitFrame = FlowFrame(stack = listOf(FlowFrameValue.Int))
        )
        val method = FlowMethod(
            ownerInternalName = "example/Test",
            name = "run",
            desc = "()I",
            blocks = mutableListOf(entry, target)
        )
        method.addEdge(
            FlowEdge(
                id = ids.edgeId(),
                from = entry,
                port = FlowPort.Next,
                to = target
            )
        )

        val result = FlowVerifier.verify(method)

        assertFalse(result.isValid)
        assertTrue(result.issues.any { "not compatible" in it.message })
    }

    @Test
    fun acceptsLocalsLengthDifferencesAsUnknownSlots() {
        val actual = FlowFrame(
            locals = listOf(
                FlowFrameValue.Object("java/lang/Object"),
                FlowFrameValue.Int,
                FlowFrameValue.Object("java/lang/Object"),
                FlowFrameValue.Int,
                FlowFrameValue.Object("java/lang/Object")
            )
        )
        val expected = FlowFrame(
            locals = listOf(
                FlowFrameValue.Object("java/lang/Object"),
                FlowFrameValue.Int,
                FlowFrameValue.Top,
                FlowFrameValue.Top,
                FlowFrameValue.Object("java/lang/Object")
            )
        )

        assertTrue(FlowFrames.isCompatible(actual, expected))
    }

    @Test
    fun plansSingleEntryRegions() {
        val ids = FlowIdAllocator()
        val entry = FlowBlock(ids.blockId(), jump = FlowGotoJump())
        val mid = FlowBlock(ids.blockId(), jump = FlowGotoJump())
        val exit = FlowBlock(ids.blockId(), jump = FlowReturnJump())
        val method = FlowMethod(
            ownerInternalName = "example/Test",
            name = "run",
            desc = "()V",
            blocks = mutableListOf(entry, mid, exit)
        )
        method.addEdge(FlowEdge(ids.edgeId(), entry, FlowPort.Next, mid))
        method.addEdge(FlowEdge(ids.edgeId(), mid, FlowPort.Next, exit))

        val regions = method.planRegions()

        assertEquals(1, regions.size)
        assertEquals(mid, regions.single().entry)
        assertEquals(setOf(mid, exit), regions.single().blocks)
        assertEquals(1, regions.single().entryEdges.size)
        assertTrue(regions.single().exitEdges.isEmpty())
    }
}
