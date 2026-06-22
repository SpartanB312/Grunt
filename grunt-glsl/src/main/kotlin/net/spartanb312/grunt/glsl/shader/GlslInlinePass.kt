package net.spartanb312.grunt.glsl.shader

internal class GlslInlinePass(
    private val options: GlslProcessOptions
) {
    fun run(documents: List<GlslDocument>, files: Map<ResourcePath, String>): InlinePassResult {
        val analyzer = GlslAnalyzer(documents)
        val analyses = documents.flatMap { document ->
            document.functions.filter { it.hasBody }.map { function ->
                analyzer.analyze(document, function)
            }
        }
        val documentByPath = documents.associateBy { it.path }
        val allCallTokens = documents.flatMap { analyzer.collectCallTokens(it) }
        val functionsByName = documents.flatMap { it.functions }.groupBy { it.name }
        val directiveIdentifiers = collectDocumentDirectiveIdentifiers(documents)
        val rawCandidates = analyses.mapNotNull { analysis ->
            val group = functionsByName[analysis.function.name].orEmpty()
            if (group.size != 1) return@mapNotNull null
            if (!isPrivateFunction(analysis.function, options)) return@mapNotNull null
            if (analysis.hasBodyDirectives) return@mapNotNull null
            if (analysis.function.name in directiveIdentifiers) return@mapNotNull null
            analysis.toInlineCandidate(documentByPath.getValue(analysis.function.file)) ?: return@mapNotNull null
        }
        val candidateNames = rawCandidates.mapTo(linkedSetOf()) { it.function.name }
        val candidates = rawCandidates.filter { it.isLeafCandidate(candidateNames) }

        val patches = mutableListOf<TextPatch>()
        var inlinedCalls = 0
        val tempNameGenerator = InlineTempNameGenerator()
        candidates.forEach { candidate ->
            val callTokens = allCallTokens.filter { it.text == candidate.function.name }
            if (callTokens.isEmpty()) return@forEach
            val supportedPatches = mutableListOf<TextPatch>()
            val supportedStatements = mutableSetOf<Pair<ResourcePath, Int>>()
            analyses.forEach { caller ->
                caller.statements.forEach { statement ->
                    val patch = buildInlinePatch(candidate, caller, statement, tempNameGenerator) ?: return@forEach
                    supportedPatches += patch
                    supportedStatements += statement.file to statement.start
                }
            }
            if (supportedPatches.isEmpty()) return@forEach
            if (supportedPatches.size > options.inlineMaxCallSitesPerFunction) return@forEach

            val allCallsSupported = callTokens.all { call ->
                analyses.any { analysis ->
                    analysis.statements.any { statement ->
                        statement.file == call.file &&
                            statement.start <= call.start &&
                            statement.end >= call.end &&
                            (statement.file to statement.start) in supportedStatements
                    }
                }
            }
            val candidatePatches = supportedPatches.toMutableList()
            if (allCallsSupported && options.removeFullyInlinedPrivateFunctions) {
                candidatePatches += TextPatch(
                    file = candidate.function.file,
                    start = candidate.function.start,
                    end = candidate.function.end,
                    replacement = ""
                )
            }

            if (!passesExpansionBudget(files, candidatePatches)) return@forEach
            candidatePatches.forEach { patch ->
                if (patches.none { overlaps(it, patch) }) {
                    patches += patch
                    if (patch.start != candidate.function.start || patch.end != candidate.function.end) {
                        inlinedCalls++
                    }
                }
            }
        }

        return InlinePassResult(applyPatches(files, patches), inlinedCalls)
    }

    private fun GlslFunctionAnalysis.toInlineCandidate(document: GlslDocument): InlineCandidate? {
        val targetFunction = this.function
        if (targetFunction.conditionalDepth > 0) return null
        if (targetFunction.parameters.any { it.isOutLike || it.isOpaque }) return null
        if (callTokens.any { it.text == targetFunction.name }) return null
        val rawBodyTokens = bodyTokens(document, targetFunction)
        if (rawBodyTokens.any { it.text in DISALLOWED_INLINE_TOKENS }) return null
        if (rawBodyTokens.any { it.text == "{" || it.text == "}" }) return null
        val statements = statements
        if (statements.isEmpty() || statements.size > options.inlineMaxStatements) return null
        val returnStatements = statements.filter { it.tokens.firstOrNull()?.text == "return" }
        if (returnStatements.size != 1 || returnStatements.single() != statements.last()) return null
        if (statements.dropLast(1).any { it.tokens.firstOrNull()?.text == "return" }) return null
        return InlineCandidate(
            function = targetFunction,
            nonReturnStatements = statements.dropLast(1),
            returnStatement = statements.last(),
            symbols = symbols
        )
    }

    private fun buildInlinePatch(
        candidate: InlineCandidate,
        caller: GlslFunctionAnalysis,
        statement: GlslStatement,
        tempNameGenerator: InlineTempNameGenerator
    ): TextPatch? {
        val callerSource = sourceFor(caller.function.file) ?: return null
        val calleeSource = sourceFor(candidate.function.file) ?: return null
        val tokens = statement.tokens
        val callIndex = tokens.indexOfFirst { token -> token.text == candidate.function.name }
        if (callIndex == -1) return null
        if (tokens.count { it.text == candidate.function.name } != 1) return null
        if (tokens.getOrNull(callIndex + 1)?.text != "(") return null
        if (tokens.getOrNull(callIndex - 1)?.text == ".") return null
        val close = findMatching(tokens, callIndex + 1, "(", ")")
        if (close == -1) return null
        val trailing = tokens.subList(close + 1, tokens.size).filter { it.text != ";" }
        if (trailing.isNotEmpty()) return null

        val args = splitTopLevel(tokens.subList(callIndex + 2, close), ",")
        if (args.size != candidate.function.parameters.size) return null
        val kind = inlineSiteKind(tokens, callIndex) ?: return null
        val indent = lineIndent(callerSource, statement.start)
        val tempMappings = linkedMapOf<String, String>()
        val lines = mutableListOf<String>()
        candidate.function.parameters.forEachIndexed { index, parameter ->
            val tempName = tempNameGenerator.next()
            tempMappings[parameter.name] = tempName
            val argText = if (args[index].isEmpty()) "" else {
                callerSource.substring(args[index].first().start, args[index].last().end)
            }
            lines += "$indent${parameter.typeText} $tempName = $argText;"
        }
        val renameMap = buildInlineRenameMap(candidate, tempMappings, tempNameGenerator)
        candidate.nonReturnStatements.forEach { original ->
            val statementText = rewriteTokenRange(calleeSource, original.tokens, renameMap).trim()
            if (statementText.isNotEmpty()) lines += "$indent$statementText"
        }
        val returnExprTokens = candidate.returnStatement.tokens
            .drop(1)
            .dropLastWhile { it.text == ";" }
        val returnExpr = rewriteTokenRange(calleeSource, returnExprTokens, renameMap).trim()
        val prefix = callerSource.substring(statement.start, tokens[callIndex].start)
        lines += when (kind) {
            InlineSiteKind.Declaration,
            InlineSiteKind.Assignment -> prefix + returnExpr + ";"

            InlineSiteKind.Return -> indent + "return " + returnExpr + ";"
        }
        return TextPatch(statement.file, statement.start, statement.end, lines.joinToString("\n"))
    }

    private fun InlineCandidate.isLeafCandidate(candidateNames: Set<String>): Boolean {
        return (nonReturnStatements + returnStatement)
            .flatMap { it.tokens }
            .windowed(2, 1)
            .none { (token, next) ->
                token.text in candidateNames &&
                    token.text != function.name &&
                    next.text == "("
            }
    }

    private fun inlineSiteKind(tokens: List<GlslToken>, callIndex: Int): InlineSiteKind? {
        if (tokens.firstOrNull()?.text == "return" && callIndex == 1) return InlineSiteKind.Return
        val beforeCall = tokens.subList(0, callIndex)
        if (beforeCall.any { it.text == "=" }) {
            return if (findDeclaration(tokens) != null) InlineSiteKind.Declaration else InlineSiteKind.Assignment
        }
        return null
    }

    private fun buildInlineRenameMap(
        candidate: InlineCandidate,
        parameterTemps: Map<String, String>,
        tempNameGenerator: InlineTempNameGenerator
    ): Map<Pair<Int, Int>, String> {
        val symbolNames = candidate.symbols
            .filter { it.kind == GlslSymbolKind.Local || it.kind == GlslSymbolKind.Parameter }
            .associateWith { symbol ->
                parameterTemps[symbol.name] ?: tempNameGenerator.next()
            }
        val replacements = linkedMapOf<Pair<Int, Int>, String>()
        symbolNames.forEach { (symbol, replacement) ->
            replacements[symbol.declaration.start to symbol.declaration.end] = replacement
            symbol.references.forEach { ref -> replacements[ref.start to ref.end] = replacement }
        }
        return replacements
    }

    private fun rewriteTokenRange(
        source: String,
        tokens: List<GlslToken>,
        replacements: Map<Pair<Int, Int>, String>
    ): String {
        if (tokens.isEmpty()) return ""
        var cursor = tokens.first().start
        return buildString {
            tokens.forEach { token ->
                append(source, cursor, token.start)
                append(replacements[token.start to token.end] ?: token.text)
                cursor = token.end
            }
            append(source, cursor, tokens.last().end)
        }
    }

    private fun sourceFor(file: ResourcePath): String? = SourceRegistry.sources[file]

    private fun passesExpansionBudget(@Suppress("UNUSED_PARAMETER") files: Map<ResourcePath, String>, patches: List<TextPatch>): Boolean {
        val original = patches.sumOf { it.end - it.start }.coerceAtLeast(1)
        val replacement = patches.sumOf { it.replacement.length }
        if (patches.any { it.replacement.isEmpty() }) return true
        val ratio = replacement.toDouble() / original.toDouble()
        return ratio <= options.inlineMaxExpansionRatio
    }

    private fun overlaps(left: TextPatch, right: TextPatch): Boolean {
        return left.file == right.file && left.start < right.end && right.start < left.end
    }

    private data class InlineCandidate(
        val function: GlslFunction,
        val nonReturnStatements: List<GlslStatement>,
        val returnStatement: GlslStatement,
        val symbols: List<GlslSymbol>
    )

    private enum class InlineSiteKind {
        Declaration,
        Assignment,
        Return
    }

    private class InlineTempNameGenerator {
        private var index = 0
        fun next(): String = "_g${index++}"
    }

    private companion object {
        val DISALLOWED_INLINE_TOKENS = setOf(
            "if", "for", "while", "do", "switch", "break", "continue", "discard"
        )
    }
}

internal fun isPrivateFunction(function: GlslFunction, options: GlslProcessOptions): Boolean {
    return function.hasBody &&
        function.name !in GLSL_BUILTIN_NAMES &&
        !function.name.startsWith("gl_") &&
        !isPreservedName(function.name, options.preserveNames)
}

internal object SourceRegistry {
    var sources: Map<ResourcePath, String> = emptyMap()
}
