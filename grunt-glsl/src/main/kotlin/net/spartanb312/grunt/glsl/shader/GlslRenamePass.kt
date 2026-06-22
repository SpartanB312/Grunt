package net.spartanb312.grunt.glsl.shader

internal class GlslRenamePass(
    private val options: GlslProcessOptions
) {
    fun run(documents: List<GlslDocument>, files: Map<ResourcePath, String>): RenamePassResult {
        val analyzer = GlslAnalyzer(documents)
        val analyses = documents.flatMap { document ->
            document.functions.filter { it.hasBody }.map { function ->
                analyzer.analyze(document, function)
            }
        }
        val patches = mutableListOf<TextPatch>()
        var localSymbolCount = 0
        var privateFunctionCount = 0
        val reserved = collectReservedNames(documents)
        val directiveIdentifiers = collectDocumentDirectiveIdentifiers(documents)

        if (options.renamePrivateFunctions) {
            val functionPatches = renamePrivateFunctions(documents, analyzer, reserved, directiveIdentifiers)
            patches += functionPatches.patches
            privateFunctionCount = functionPatches.count
        }

        analyses.forEach { analysis ->
            if (analysis.hasBodyDirectives) return@forEach
            val generator = GlslNameGenerator(reserved.toMutableSet())
            analysis.symbols.forEach { symbol ->
                val enabled = when (symbol.kind) {
                    GlslSymbolKind.Parameter -> options.renameParameters
                    GlslSymbolKind.Local -> options.renameLocals
                }
                if (!enabled) return@forEach
                if (isPreservedName(symbol.name, options.preserveNames)) return@forEach
                if (symbol.name in analysis.directiveIdentifiers) return@forEach
                val newName = generator.next()
                patches += TextPatch(symbol.declaration.file, symbol.declaration.start, symbol.declaration.end, newName)
                symbol.references.forEach { ref ->
                    patches += TextPatch(ref.file, ref.start, ref.end, newName)
                }
                localSymbolCount++
            }
        }

        return RenamePassResult(
            files = applyPatches(files, patches),
            renamedLocalSymbols = localSymbolCount,
            renamedPrivateFunctions = privateFunctionCount
        )
    }

    private fun renamePrivateFunctions(
        documents: List<GlslDocument>,
        analyzer: GlslAnalyzer,
        reserved: MutableSet<String>,
        directiveIdentifiers: Set<String>
    ): FunctionRenameResult {
        val allFunctions = documents.flatMap { it.functions }
        val calls = documents.flatMap { analyzer.collectCallTokens(it) }
        val patches = mutableListOf<TextPatch>()
        var count = 0
        val generator = GlslNameGenerator(reserved)
        allFunctions.groupBy { it.name }.forEach { (name, functions) ->
            val function = functions.singleOrNull() ?: return@forEach
            if (!isPrivateFunction(function, options)) return@forEach
            if (function.conditionalDepth > 0) return@forEach
            if (name in directiveIdentifiers) return@forEach
            val newName = generator.next()
            patches += TextPatch(function.file, function.nameToken.start, function.nameToken.end, newName)
            calls.filter { it.text == name }.forEach { call ->
                patches += TextPatch(call.file, call.start, call.end, newName)
            }
            count++
        }
        return FunctionRenameResult(patches, count)
    }

    private fun collectReservedNames(documents: List<GlslDocument>): MutableSet<String> {
        val names = linkedSetOf<String>()
        names += GLSL_KEYWORDS
        names += GLSL_BUILTIN_NAMES
        options.preserveNames.filterNot { "*" in it }.forEach { names += it }
        documents.forEach { document ->
            document.tokens.forEach { token ->
                if (token.isIdentifierLike) names += token.text
            }
        }
        names += collectDocumentDirectiveIdentifiers(documents)
        return names
    }

    private data class FunctionRenameResult(
        val patches: List<TextPatch>,
        val count: Int
    )
}
