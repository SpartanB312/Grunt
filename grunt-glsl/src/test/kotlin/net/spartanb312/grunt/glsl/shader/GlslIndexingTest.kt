package net.spartanb312.grunt.glsl.shader

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GlslIndexingTest {
    @Test
    fun bodyViewsAndAnalysisVisitOnlyFunctionTokensAtScale() {
        for (count in listOf(32, 128, 512)) {
            val source = buildString {
                append("float prototype(float x); void empty() {}\n")
                repeat(count) { index ->
                    append("float f$index(float x) { float y = x; { float x = y; y = x + 1.0; } return y; }\n")
                }
            }
            val original = GlslParser.parse("scale.glsl", source)
            val counted = CountingTokens(original.significantTokens)
            val document = original.copy(significantTokens = counted)
            val views = document.functions.map { bodyTokens(document, it) }
            assertEquals(0, counted.reads, "Creating body views must not scan the document")
            document.functions.zip(views).forEach { (function, view) ->
                val expected = original.significantTokens.filter {
                    function.hasBody && it.start > function.bodyOpen!!.start && it.end < function.bodyClose!!.end
                }
                assertEquals(expected, view)
            }
            counted.reads = 0
            val analyzer = GlslAnalyzer(listOf(document))
            document.functions.forEach { function ->
                val analysis = analyzer.analyze(document, function)
                if (function.name.startsWith("f")) {
                    val shadowed = analysis.symbols.filter { it.name == "x" }
                    assertEquals(2, shadowed.size)
                    assertEquals(listOf(1, 1), shadowed.map { it.references.size })
                    assertEquals(4, analysis.statements.size)
                }
            }
            assertTrue(counted.reads <= original.significantTokens.size * 8,
                "Analysis should scale with tokens, not functions × tokens: ${counted.reads}")
        }
    }

    @Test
    fun callStatementIndexMatchesContainmentIncludingUnsupportedSites() {
        val source = """
            float helper(float x);
            float caller(float x) {
                if (helper(x) > 0.0) {
                    float a = helper(x);
                    a = helper(a) + helper(x);
                }
                for (int i = 0; helper(x) > 0.0; i++) {
                    x = helper(x);
                }
                return helper(x);
            }
        """.trimIndent()
        val document = GlslParser.parse("index.glsl", source)
        val analysis = document.analysis
        assertEquals(7, analysis.callSites.size)
        analysis.callSites.forEach { site ->
            val owner = analysis.functions.firstOrNull { function ->
                function.statements.any { it.start <= site.token.start && site.token.end <= it.end }
            }
            val statement = owner?.statements?.firstOrNull {
                it.start <= site.token.start && site.token.end <= it.end
            }
            assertSame(owner, site.caller)
            assertSame(statement, site.statement)
            assertEquals("helper", source.substring(site.token.start, site.token.end))
        }
        assertEquals(2, analysis.callSites.count { it.statement == null })
    }

    @Test
    fun indexesThousandsOfCallsInSourceOrder() {
        val count = 2048
        val source = buildString {
            repeat(count) { index ->
                append("float f$index(float x) { float y = helper(x); y = helper(y); return helper(y); }\n")
            }
        }
        val analysis = GlslParser.parse("scale.glsl", source).analysis
        assertEquals(count * 3, analysis.callSites.size)
        analysis.callSites.forEachIndexed { index, site ->
            assertEquals("f${index / 3}", site.caller!!.function.name)
            assertTrue(site.statement != null)
        }
        assertTrue(analysis.callSites.zipWithNext().all { (a, b) -> a.token.start < b.token.start })
    }

    @Test
    fun intervalIndexMatchesFirstAcceptedOverlapSemantics() {
        val random = Random(923)
        val index = GlslPatchIndex()
        val accepted = mutableListOf<TextPatch>()
        repeat(4096) {
            val start = random.nextInt(2000)
            val patch = TextPatch("file${random.nextInt(3)}", start, start + random.nextInt(1, 40), "x")
            val expected = accepted.none {
                it.file == patch.file && it.start < patch.end && patch.start < it.end
            }
            assertEquals(expected, index.tryAdd(patch))
            if (expected) accepted += patch
        }
    }

    @Test
    fun intervalIndexHandlesLargeTouchingRangesAndDifferentFiles() {
        val index = GlslPatchIndex()
        (0 until 32768).shuffled(Random(74)).forEach { start ->
            assertTrue(index.tryAdd(TextPatch("a", start * 2, start * 2 + 2, "x")))
        }
        assertFalse(index.tryAdd(TextPatch("a", 1, 65535, "x")))
        assertTrue(index.tryAdd(TextPatch("b", 1, 65535, "x")))
        assertTrue(index.tryAdd(TextPatch("a", 65536, 65538, "x")))
    }

    private class CountingTokens(private val tokens: List<GlslToken>) : AbstractList<GlslToken>() {
        var reads = 0
        override val size: Int get() = tokens.size
        override fun get(index: Int): GlslToken {
            reads++
            return tokens[index]
        }
    }
}
