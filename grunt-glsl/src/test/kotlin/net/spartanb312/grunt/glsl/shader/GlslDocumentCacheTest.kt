package net.spartanb312.grunt.glsl.shader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GlslDocumentCacheTest {
    @Test
    fun reusesLocalAnalysisOnlyForTheSamePathAndSourceRevision() {
        val parses = mutableListOf<String>()
        val cache = GlslDocumentCache { path, source ->
            parses += path
            GlslParser.parse(path, source)
        }
        val source = "float f(float x) { float y = x; return y; }"
        val original = cache.update(linkedMapOf("a" to source, "b" to source))
        val originalAnalysis = original.first().analysis
        val unchanged = cache.update(linkedMapOf("b" to source, "a" to String(source.toCharArray())))
        assertSame(original[0], unchanged[1])
        assertSame(originalAnalysis, unchanged[1].analysis)
        assertSame(original[1], unchanged[0])
        assertNotSame(original[0].analysis, original[1].analysis)
        assertEquals(listOf("a", "b"), parses)

        val changed = cache.update(linkedMapOf("a" to "\n\n$source", "b" to source))
        assertNotSame(original[0], changed[0])
        assertSame(original[1], changed[1])
        val oldSymbol = originalAnalysis.functions.single().symbols.last()
        val newSymbol = changed[0].analysis.functions.single().symbols.last()
        assertEquals(oldSymbol.declaration.start + 2, newSymbol.declaration.start)
        assertEquals(oldSymbol.references.single().start + 2, newSymbol.references.single().start)
        cache.update(mapOf("b" to source))
        val restored = cache.update(linkedMapOf("a" to source, "b" to source))
        assertNotSame(original[0], restored[0])
        assertEquals(listOf("a", "b", "a", "a"), parses)
    }

    @Test
    fun unchangedInlineIterationIsReusedForRenameAndUnchangedFilesAreNotReparsed() {
        val sources = linkedMapOf(
            "main.glsl" to "float helper(float x) { return x; } void main() { float y = helper(1.0); }",
            "stable.glsl" to "uniform float stable;"
        )
        val parses = mutableListOf<String>()
        val result = GlslProcessor(testOptions()) { path, source ->
            parses += path
            GlslParser.parse(path, source)
        }.process(sources)
        assertEquals(1, result.stats.inlinedCalls)
        assertEquals(listOf("main.glsl", "stable.glsl", "main.glsl"), parses)
        assertEquals(2, result.stats.parsedFiles) // This statistic remains a file count, not parse invocations.
        assertEquals(processWithoutCache(sources, testOptions()), result)
    }

    @Test
    fun stoppedAndDisabledInliningParseEachFileOnlyOnce() {
        for (enabled in listOf(false, true)) {
            var parses = 0
            GlslProcessor(testOptions(inlineEnabled = enabled)) { path, source ->
                parses++
                GlslParser.parse(path, source)
            }.process(mapOf("a" to "void main() {}", "b" to "uniform float value;"))
            assertEquals(2, parses)
        }
    }

    @Test
    fun reparseAfterTheIterationLimitPreventsStaleRenameOffsets() {
        val source = buildString {
            append("float f0(float x) { return x + 1.0; }\n")
            for (index in 1..9) append("float f$index(float x) { return f${index - 1}(x); }\n")
            append("void main() { float result = f9(2.0); }")
        }
        val options = testOptions().copy(inlineMaxStatements = 64, inlineMaxExpansionRatio = 100.0)
        val parses = mutableListOf<GlslDocument>()
        val files = mapOf("chain.glsl" to source)
        val actual = GlslProcessor(options) { path, text ->
            GlslParser.parse(path, text).also { parses += it }
        }.process(files)
        assertEquals(8, actual.stats.inlinedCalls)
        assertEquals(9, parses.size)
        assertEquals(processWithoutCache(files, options), actual)
        assertContains(actual.files.getValue("chain.glsl"), "void main()")
    }

    @Test
    fun foreignOverloadsAndDirectivesInvalidateEligibilityNotLocalAnalysis() {
        val source = "float helper(float x) { return x; } void main() { float y = helper(1.0); }"
        val cache = GlslDocumentCache()
        val base = cache.update(linkedMapOf("main" to source, "other" to ""))
        val local = base.first().analysis
        val options = testOptions(renameLocals = false, renameParameters = false)
        for (dependency in listOf("float helper(float x);", "#define APPLY helper\n")) {
            val files = linkedMapOf("main" to source, "other" to dependency)
            val documents = cache.update(files)
            assertSame(base.first(), documents.first())
            assertSame(local, documents.first().analysis)
            val inline = GlslInlinePass(options).run(documents, files)
            assertEquals(0, inline.inlinedCalls)
            assertEquals(files, inline.files)
            val renamed = GlslRenamePass(options).run(documents, files)
            assertEquals(0, renamed.renamedPrivateFunctions)
            assertEquals(files, renamed.files)
        }
        val files = linkedMapOf("main" to source, "other" to "")
        assertEquals(1, GlslInlinePass(options).run(cache.update(files), files).inlinedCalls)
    }

    @Test
    fun changedCalleeUpdatesUnchangedCrossFileCallerWithoutSharedSourceRegistry() {
        val main = "#include \"math.glsl\"\nvoid main() { float value = helper(2.0); }"
        val cache = GlslDocumentCache()
        val options = testOptions(renamePrivateFunctions = false, renameLocals = false, renameParameters = false)
        val initial = linkedMapOf("main.glsl" to main, "math.glsl" to "float helper(float x) { return x + 1.0; }")
        val first = cache.update(initial)
        GlslInlinePass(options).run(first, initial)
        val changed = initial + ("math.glsl" to "float helper(float x) { return x * 3.0; }")
        val current = cache.update(changed)
        assertSame(first[0].analysis, current[0].analysis)
        assertNotSame(first[1].analysis, current[1].analysis)
        val result = GlslInlinePass(options).run(current, changed)
        assertEquals(GlslInlinePass(options).run(changed.map { GlslParser.parse(it.key, it.value) }, changed), result)
        assertContains(result.files.getValue("main.glsl"), "_g0 * 3.0")
        assertTrue(result.files.getValue("math.glsl").isBlank())
    }

    private fun processWithoutCache(input: Map<String, String>, options: GlslProcessOptions): GlslProcessResult {
        var files = input
        var inlinedCalls = 0
        if (options.inlineEnabled) {
            for (iteration in 0 until 8) {
                val documents = files.map { GlslParser.parse(it.key, it.value) }
                val result = GlslInlinePass(options).run(documents, files)
                if (result.inlinedCalls == 0) break
                files = result.files
                inlinedCalls += result.inlinedCalls
            }
        }
        val documents = files.map { GlslParser.parse(it.key, it.value) }
        val renamed = GlslRenamePass(options).run(documents, files)
        return GlslProcessResult(renamed.files, GlslStats(
            scannedFiles = input.size, parsedFiles = input.size, inlinedCalls = inlinedCalls,
            renamedLocalSymbols = renamed.renamedLocalSymbols,
            renamedPrivateFunctions = renamed.renamedPrivateFunctions
        ))
    }
}
