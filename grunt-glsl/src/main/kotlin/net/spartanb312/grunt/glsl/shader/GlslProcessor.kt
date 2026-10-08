package net.spartanb312.grunt.glsl.shader

internal class GlslProcessor(
    private val options: GlslProcessOptions,
    private val parseDocument: (ResourcePath, String) -> GlslDocument = GlslParser::parse
) {
    fun process(inputFiles: Map<ResourcePath, String>): GlslProcessResult {
        val normalizedFiles = inputFiles.mapKeys { normalizeResourcePath(it.key) }
        val includeGraph = GlslIncludeResolver(
            includeRoots = options.includeRoots,
            failOnMissingIncludes = options.failOnMissingIncludes
        ).resolve(normalizedFiles)
        val documentCache = GlslDocumentCache(parseDocument)
        val inlineResult = runInlinePasses(normalizedFiles, documentCache)
        val afterInline = inlineResult.files
        val documents = documentCache.update(afterInline)
        val renameResult = GlslRenamePass(options).run(documents, afterInline)
        val stats = GlslStats(
            scannedFiles = normalizedFiles.size,
            parsedFiles = documents.size,
            includeEdges = includeGraph.edgeCount,
            includeWarnings = includeGraph.warnings,
            inlinedCalls = inlineResult.inlinedCalls,
            renamedLocalSymbols = renameResult.renamedLocalSymbols,
            renamedPrivateFunctions = renameResult.renamedPrivateFunctions
        )
        return GlslProcessResult(renameResult.files, stats)
    }

    private fun runInlinePasses(
        files: Map<ResourcePath, String>,
        documentCache: GlslDocumentCache
    ): InlinePassResult {
        if (!options.inlineEnabled) return InlinePassResult(files, 0)
        var currentFiles = files
        var totalInlined = 0
        repeat(MAX_INLINE_ITERATIONS) {
            val documents = documentCache.update(currentFiles)
            val result = GlslInlinePass(options).run(documents, currentFiles)
            if (result.inlinedCalls == 0) return InlinePassResult(currentFiles, totalInlined)
            currentFiles = result.files
            totalInlined += result.inlinedCalls
        }
        return InlinePassResult(currentFiles, totalInlined)
    }

    private companion object {
        const val MAX_INLINE_ITERATIONS = 8
    }
}

// A source string is the revision key: offsets, scopes and call sites belong to that exact source.
// No include/overload/directive eligibility is cached here; passes rebuild those global facts.
internal class GlslDocumentCache(
    private val parseDocument: (ResourcePath, String) -> GlslDocument = GlslParser::parse
) {
    private val documents = mutableMapOf<ResourcePath, GlslDocument>()

    fun update(files: Map<ResourcePath, String>): List<GlslDocument> {
        documents.keys.retainAll(files.keys)
        return files.map { (path, source) ->
            documents[path]?.takeIf { it.source == source } ?: run {
                val document = try {
                    parseDocument(path, source)
                } catch (error: Exception) {
                    if (error is GlslObfuscationException) throw error
                    throw GlslObfuscationException("Failed to parse GLSL file $path: ${error.message}")
                }
                documents[path] = document
                document
            }
        }
    }
}

internal data class InlinePassResult(
    val files: Map<ResourcePath, String>,
    val inlinedCalls: Int
)

internal data class RenamePassResult(
    val files: Map<ResourcePath, String>,
    val renamedLocalSymbols: Int,
    val renamedPrivateFunctions: Int
)
