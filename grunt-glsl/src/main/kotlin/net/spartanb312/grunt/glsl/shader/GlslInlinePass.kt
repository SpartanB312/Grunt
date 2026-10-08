package net.spartanb312.grunt.glsl.shader

internal class GlslInlinePass(
    private val options: GlslProcessOptions
) {
    fun run(documents: List<GlslDocument>, files: Map<ResourcePath, String>): InlinePassResult {
        val analyses = documents.flatMap { it.analysis.functions }
        val documentByPath = documents.associateBy { it.path }
        val callsByName = documents.flatMap { it.analysis.callSites }.groupBy { it.token.text }
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

        val plan = GlslInlinePatchPlan(options.inlineMaxExpansionRatio)
        val tempNameGenerator = InlineTempNameGenerator()
        candidates.forEach { candidate ->
            val callSites = callsByName[candidate.function.name].orEmpty()
            if (callSites.isEmpty()) return@forEach
            val supportedPatches = mutableListOf<TextPatch>()
            val visitedStatements = mutableSetOf<Pair<ResourcePath, Int>>()
            val supportedStatements = mutableSetOf<Pair<ResourcePath, Int>>()
            // Source order matches the former caller/statement traversal, including temporary names.
            callSites.forEach call@ { site ->
                val statement = site.statement ?: return@call
                val key = statement.file to statement.start
                if (!visitedStatements.add(key)) return@call
                val patch = buildInlinePatch(
                    candidate, statement, files.getValue(statement.file),
                    files.getValue(candidate.function.file), tempNameGenerator
                ) ?: return@call
                supportedPatches += patch
                supportedStatements += key
            }
            if (supportedPatches.isEmpty()) return@forEach
            if (supportedPatches.size > options.inlineMaxCallSitesPerFunction) return@forEach

            val allCallsSupported = callSites.all { site ->
                site.statement?.let { (it.file to it.start) in supportedStatements } == true
            }
            val deletion = if (allCallsSupported && options.removeFullyInlinedPrivateFunctions) {
                TextPatch(candidate.function.file, candidate.function.start, candidate.function.end, "")
            } else null
            plan.accept(supportedPatches, deletion)
        }

        return InlinePassResult(applyPatches(files, plan.patches), plan.inlinedCalls)
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
        statement: GlslStatement,
        callerSource: String,
        calleeSource: String,
        tempNameGenerator: InlineTempNameGenerator
    ): TextPatch? {
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

internal class GlslInlinePatchPlan(private val maxExpansionRatio: Double) {
    val patches = mutableListOf<TextPatch>()
    var inlinedCalls = 0
        private set
    private val accepted = GlslPatchIndex()

    fun accept(callPatches: List<TextPatch>, deletion: TextPatch?) {
        val staged = GlslPatchIndex()
        val candidatePatches = callPatches.filterTo(mutableListOf()) { patch ->
            !accepted.overlaps(patch) && staged.tryAdd(patch)
        }
        if (candidatePatches.isEmpty()) return
        val acceptedCalls = candidatePatches.size
        // A rejected call patch must never authorize deleting its still-referenced definition.
        if (deletion != null && acceptedCalls == callPatches.size &&
            !accepted.overlaps(deletion) && staged.tryAdd(deletion)
        ) {
            candidatePatches += deletion
        }
        // Preserve the patch-span ratio (not the whole file size). Only accepted deletions save space.
        val original = candidatePatches.sumOf { it.end.toLong() - it.start }.coerceAtLeast(1L)
        val replacement = candidatePatches.sumOf { it.replacement.length.toLong() }
        if (!(replacement.toDouble() / original.toDouble() <= maxExpansionRatio)) return
        candidatePatches.forEach { patch ->
            check(accepted.tryAdd(patch))
            patches += patch
        }
        inlinedCalls += acceptedCalls
    }
}

internal fun isPrivateFunction(function: GlslFunction, options: GlslProcessOptions): Boolean {
    return function.hasBody &&
        function.name !in GLSL_BUILTIN_NAMES &&
        !function.name.startsWith("gl_") &&
        !isPreservedName(function.name, options.preserveNames)
}
