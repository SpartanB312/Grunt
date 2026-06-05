package net.spartanb312.grunt.ir.flow.visual

import net.spartanb312.grunt.ir.flow.core.FlowBlock
import net.spartanb312.grunt.ir.flow.core.FlowBlockKind
import net.spartanb312.grunt.ir.flow.core.FlowBytecodeSlice
import net.spartanb312.grunt.ir.flow.core.FlowEdge
import net.spartanb312.grunt.ir.flow.core.FlowEdgeSemantics
import net.spartanb312.grunt.ir.flow.core.FlowFrame
import net.spartanb312.grunt.ir.flow.core.FlowFrameValue
import net.spartanb312.grunt.ir.flow.core.FlowGotoJump
import net.spartanb312.grunt.ir.flow.core.FlowGotoMode
import net.spartanb312.grunt.ir.flow.core.FlowIdAllocator
import net.spartanb312.grunt.ir.flow.core.FlowIfJump
import net.spartanb312.grunt.ir.flow.core.FlowJumpInput
import net.spartanb312.grunt.ir.flow.core.FlowMethod
import net.spartanb312.grunt.ir.flow.core.FlowPort
import net.spartanb312.grunt.ir.flow.core.FlowPredicateGuarantee
import net.spartanb312.grunt.ir.flow.core.FlowReturnJump
import org.objectweb.asm.Opcodes
import kotlin.test.Test
import kotlin.test.assertContains

class FlowDotExporterTest {
    @Test
    fun printsBlockAndEdgeDetails() {
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
        val real = FlowBlock(
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
            blocks = mutableListOf(entry, real, bogus)
        )
        method.addEdge(FlowEdge(ids.edgeId(), entry, FlowPort.Branch, real, FlowEdgeSemantics.Real))
        method.addEdge(FlowEdge(ids.edgeId(), entry, FlowPort.Fallthrough, bogus, FlowEdgeSemantics.Bogus))
        method.addEdge(FlowEdge(ids.edgeId(), bogus, FlowPort.Next, real, FlowEdgeSemantics.Synthetic))

        val dot = FlowDotExporter().print(method)

        assertContains(dot, "digraph")
        assertContains(dot, "\"fb0\"")
        assertContains(dot, "IF IFEQ")
        assertContains(dot, "generated[int] AlwaysTrue")
        assertContains(dot, "Bogus")
        assertContains(dot, "branch / Real")
        assertContains(dot, "fallthrough / Bogus")
        assertContains(dot, "style=\"dashed\"")
    }
}
