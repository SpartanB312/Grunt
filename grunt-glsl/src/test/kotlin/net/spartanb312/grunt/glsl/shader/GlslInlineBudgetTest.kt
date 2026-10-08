package net.spartanb312.grunt.glsl.shader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlslInlineBudgetTest {
    private val options = testOptions(renamePrivateFunctions = false, renameLocals = false, renameParameters = false)

    @Test
    fun deletionDoesNotBypassBudgetForEightStatementHelperWithSixteenCalls() {
        val source = buildString {
            append("float helper(float x) { float a = x; ")
            repeat(6) { append("a = a * 1.25 + 2.0; ") }
            append("return a; }\nvoid main() {\n")
            repeat(16) { append("float value$it = helper(1.0);\n") }
            append("}")
        }
        val rejected = inline(source, options.copy(inlineMaxExpansionRatio = 1.5))
        assertEquals(0, rejected.inlinedCalls)
        assertEquals(source, rejected.files.getValue("a"))
        val accepted = inline(source, options.copy(inlineMaxExpansionRatio = 10.0))
        assertEquals(16, accepted.inlinedCalls)
        val output = accepted.files.getValue("a")
        assertFalse("helper(" in output)
        assertTrue(output.length > source.length * 1.5)
        val document = GlslParser.parse("a", source)
        val helper = document.functions.first()
        val callSpans = document.analysis.functions.last().statements.sumOf { it.end - it.start }
        val originalSpans = callSpans + helper.end - helper.start
        val replacementSize = output.length - (source.length - originalSpans)
        assertTrue(replacementSize.toDouble() / originalSpans > 1.5)
        assertTrue(replacementSize.toDouble() / originalSpans <= 10.0)
    }

    @Test
    fun ratioUsesAcceptedPatchSpansIncludingDeletionNotWholeFilePadding() {
        val helper = "float helper(float x) { return x; }"
        val call = "float y = helper(1.0);"
        val replacement = "float _g0 = 1.0;\nfloat y = _g0;"
        val source = "$helper\nvoid main() { $call }"
        val deletionRatio = replacement.length.toDouble() / (helper.length + call.length)
        val callOnlyRatio = replacement.length.toDouble() / call.length
        assertTrue(deletionRatio < 1.0)
        assertTrue(callOnlyRatio > 1.0)
        for (padding in listOf("", "/*" + "padding".repeat(1024) + "*/\n")) {
            val input = padding + source
            val exact = inline(input, options.copy(inlineMaxExpansionRatio = deletionRatio))
            assertEquals(1, exact.inlinedCalls)
            assertEquals(padding + "\nvoid main() { $replacement }", exact.files.getValue("a"))
            val below = inline(input, options.copy(inlineMaxExpansionRatio = Math.nextDown(deletionRatio)))
            assertEquals(0, below.inlinedCalls)
            assertEquals(input, below.files.getValue("a"))
            val retained = inline(input, options.copy(
                inlineMaxExpansionRatio = deletionRatio, removeFullyInlinedPrivateFunctions = false
            ))
            assertEquals(0, retained.inlinedCalls)
            val withoutDeletion = inline(input, options.copy(
                inlineMaxExpansionRatio = callOnlyRatio, removeFullyInlinedPrivateFunctions = false
            ))
            assertEquals(1, withoutDeletion.inlinedCalls)
            assertEquals(input.replace(call, replacement), withoutDeletion.files.getValue("a"))
        }
    }

    @Test
    fun rejectedOverlappingCallPreventsDeletionAndItsBudgetCredit() {
        val initial = TextPatch("a", 0, 10, "x".repeat(10))
        val skipped = TextPatch("a", 0, 10, "x")
        val deletion = TextPatch("a", 40, 100, "")
        val plan = GlslInlinePatchPlan(1.5)
        plan.accept(listOf(initial), null)
        plan.accept(listOf(skipped, TextPatch("a", 20, 30, "x".repeat(16))), deletion)
        assertEquals(listOf(initial), plan.patches) // 16/10, not 17/80: the definition must remain.
        assertEquals(1, plan.inlinedCalls)
        val exact = TextPatch("a", 20, 30, "x".repeat(15))
        plan.accept(listOf(skipped, exact), deletion)
        assertEquals(listOf(initial, exact), plan.patches) // Exactly 15/10 is permitted.
        assertEquals(2, plan.inlinedCalls)
    }

    @Test
    fun rejectedOverlappingDeletionCannotSubsidizeAcceptedCalls() {
        val initial = TextPatch("a", 50, 60, "x".repeat(10))
        val plan = GlslInlinePatchPlan(1.5)
        plan.accept(listOf(initial), null)
        plan.accept(listOf(TextPatch("a", 20, 30, "x".repeat(16))), TextPatch("a", 40, 100, ""))
        assertEquals(listOf(initial), plan.patches)
        assertEquals(1, plan.inlinedCalls)
    }

    @Test
    fun budgetRejectionDoesNotReserveIntervalsOrChangeAcceptedCounts() {
        val plan = GlslInlinePatchPlan(1.5)
        plan.accept(listOf(TextPatch("a", 0, 10, "x".repeat(16))), null)
        assertTrue(plan.patches.isEmpty())
        val exact = TextPatch("a", 0, 10, "x".repeat(15))
        plan.accept(listOf(exact), null)
        assertEquals(listOf(exact), plan.patches)
        assertEquals(1, plan.inlinedCalls)
        val invalidRatio = GlslInlinePatchPlan(Double.NaN)
        invalidRatio.accept(listOf(exact), null)
        assertTrue(invalidRatio.patches.isEmpty())
    }

    @Test
    fun unsupportedControlHeaderCallKeepsDefinitionAfterOtherCallsAreInlined() {
        val source = """
            float helper(float x) { return x + 1.0; }
            void main() {
                if (helper(1.0) > 0.0) {
                    float value = helper(2.0);
                }
            }
        """.trimIndent()
        val result = inline(source, options)
        assertEquals(1, result.inlinedCalls)
        assertTrue("float helper(" in result.files.getValue("a"))
        assertTrue("if (helper(1.0)" in result.files.getValue("a"))
    }

    private fun inline(source: String, config: GlslProcessOptions): InlinePassResult {
        return GlslInlinePass(config).run(listOf(GlslParser.parse("a", source)), mapOf("a" to source))
    }
}
