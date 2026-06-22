package net.spartanb312.grunt.glsl.shader

internal class GlslProcessor(
    private val options: GlslProcessOptions
) {
    fun process(inputFiles: Map<ResourcePath, String>): GlslProcessResult {
        val normalizedFiles = inputFiles.mapKeys { normalizeResourcePath(it.key) }
        val includeGraph = GlslIncludeResolver(
            includeRoots = options.includeRoots,
            failOnMissingIncludes = options.failOnMissingIncludes
        ).resolve(normalizedFiles)
        val inlineResult = runInlinePasses(normalizedFiles)
        val afterInline = inlineResult.files
        SourceRegistry.sources = afterInline
        val documents = parseAll(afterInline)
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

    private fun parseAll(files: Map<ResourcePath, String>): List<GlslDocument> {
        return files.map { (path, source) ->
            runCatching { GlslParser.parse(path, source) }
                .getOrElse { error ->
                    if (error is GlslObfuscationException) throw error
                    throw GlslObfuscationException("Failed to parse GLSL file $path: ${error.message}")
                }
        }
    }

    private fun runInlinePasses(files: Map<ResourcePath, String>): InlinePassResult {
        if (!options.inlineEnabled) return InlinePassResult(files, 0)
        var currentFiles = files
        var totalInlined = 0
        repeat(MAX_INLINE_ITERATIONS) {
            SourceRegistry.sources = currentFiles
            val documents = parseAll(currentFiles)
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

internal data class InlinePassResult(
    val files: Map<ResourcePath, String>,
    val inlinedCalls: Int
)

internal data class RenamePassResult(
    val files: Map<ResourcePath, String>,
    val renamedLocalSymbols: Int,
    val renamedPrivateFunctions: Int
)
