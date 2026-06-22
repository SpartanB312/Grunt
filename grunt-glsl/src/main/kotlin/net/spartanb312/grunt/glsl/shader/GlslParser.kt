package net.spartanb312.grunt.glsl.shader

internal object GlslParser {
    fun parse(path: ResourcePath, source: String): GlslDocument {
        val tokens = GlslLexer.lex(path, source)
        val significant = tokens.filter {
            it.kind != GlslTokenKind.Whitespace &&
                it.kind != GlslTokenKind.Comment &&
                it.kind != GlslTokenKind.NewLine &&
                it.kind != GlslTokenKind.Directive
        }
        val directives = tokens
            .filter { it.kind == GlslTokenKind.Directive }
            .map { GlslDirective(path, it.text, it.start, it.end, it.conditionalDepth) }
        val functions = parseFunctions(path, source, significant)
        val globals = parseGlobalDeclarations(path, significant)
        return GlslDocument(path, source, tokens, significant, directives, globals, functions)
    }

    private fun parseFunctions(path: ResourcePath, source: String, tokens: List<GlslToken>): List<GlslFunction> {
        val functions = mutableListOf<GlslFunction>()
        var index = 0
        var braceDepth = 0
        while (index < tokens.size) {
            val token = tokens[index]
            when (token.text) {
                "{" -> {
                    braceDepth++
                    index++
                    continue
                }

                "}" -> {
                    if (braceDepth > 0) braceDepth--
                    index++
                    continue
                }
            }

            if (braceDepth == 0 &&
                token.kind == GlslTokenKind.Identifier &&
                tokens.getOrNull(index + 1)?.text == "("
            ) {
                val closeParen = findMatching(tokens, index + 1, "(", ")")
                if (closeParen != -1) {
                    val afterParen = tokens.getOrNull(closeParen + 1)
                    if (afterParen?.text == "{" || afterParen?.text == ";") {
                        val segmentStart = findFunctionSegmentStart(tokens, index)
                        val parameters = parseParameters(source, tokens.subList(index + 2, closeParen))
                        if (afterParen.text == "{") {
                            val closeBrace = findMatching(tokens, closeParen + 1, "{", "}")
                            if (closeBrace == -1) {
                                throw GlslObfuscationException("Unclosed function body for ${token.text} in $path")
                            }
                            functions += GlslFunction(
                                file = path,
                                name = token.text,
                                nameToken = token,
                                start = tokens[segmentStart].start,
                                end = tokens[closeBrace].end,
                                parameters = parameters,
                                bodyOpen = afterParen,
                                bodyClose = tokens[closeBrace],
                                conditionalDepth = token.conditionalDepth
                            )
                            index = closeBrace + 1
                            continue
                        } else {
                            functions += GlslFunction(
                                file = path,
                                name = token.text,
                                nameToken = token,
                                start = tokens[segmentStart].start,
                                end = afterParen.end,
                                parameters = parameters,
                                bodyOpen = null,
                                bodyClose = null,
                                conditionalDepth = token.conditionalDepth
                            )
                            index = closeParen + 2
                            continue
                        }
                    }
                }
            }

            index++
        }
        return functions
    }

    private fun parseParameters(source: String, tokens: List<GlslToken>): List<GlslParameter> {
        if (tokens.isEmpty() || (tokens.size == 1 && tokens[0].text == "void")) return emptyList()
        return splitTopLevel(tokens, ",").mapNotNull { segment ->
            val nameToken = segment.asReversed().firstOrNull { it.kind == GlslTokenKind.Identifier } ?: return@mapNotNull null
            val qualifiers = segment
                .takeWhile { it != nameToken }
                .mapTo(linkedSetOf()) { it.text }
                .filterTo(linkedSetOf()) { it in GLSL_QUALIFIERS }
            val typeTokens = segment
                .takeWhile { it != nameToken }
                .filterNot { it.text in setOf("in", "out", "inout", "const") }
            val typeText = if (typeTokens.isEmpty()) {
                "float"
            } else {
                source.substring(typeTokens.first().start, typeTokens.last().end)
            }.trim()
            GlslParameter(nameToken.text, nameToken, typeText, qualifiers)
        }
    }

    private fun findFunctionSegmentStart(tokens: List<GlslToken>, nameIndex: Int): Int {
        var index = nameIndex - 1
        while (index >= 0) {
            val text = tokens[index].text
            if (text == ";" || text == "}") return index + 1
            index--
        }
        return 0
    }

    private fun parseGlobalDeclarations(path: ResourcePath, tokens: List<GlslToken>): List<GlslGlobalDeclaration> {
        val declarations = mutableListOf<GlslGlobalDeclaration>()
        var index = 0
        var segmentStart = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (token.text == "{") {
                val close = findMatching(tokens, index, "{", "}")
                if (close == -1) throw GlslObfuscationException("Unclosed global block in $path")
                index = close + 1
                segmentStart = index
                continue
            }
            if (token.text == ";") {
                val segment = tokens.subList(segmentStart, index + 1)
                if (segment.isNotEmpty() && !looksLikeFunctionPrototype(segment)) {
                    val declaration = findDeclaration(segment)
                    if (declaration != null) {
                        val texts = segment.mapTo(linkedSetOf()) { it.text }
                        declarations += GlslGlobalDeclaration(
                            file = path,
                            start = segment.first().start,
                            end = segment.last().end,
                            names = declaration.declarations,
                            isPublicApi = texts.any { it in PUBLIC_GLOBAL_MARKERS }
                        )
                    }
                }
                segmentStart = index + 1
            }
            index++
        }
        return declarations
    }

    private fun looksLikeFunctionPrototype(tokens: List<GlslToken>): Boolean {
        return tokens.indices.any { index ->
            tokens[index].kind == GlslTokenKind.Identifier &&
                tokens.getOrNull(index + 1)?.text == "(" &&
                findMatching(tokens, index + 1, "(", ")").let { it != -1 && tokens.getOrNull(it + 1)?.text == ";" }
        }
    }

    private val PUBLIC_GLOBAL_MARKERS = setOf("uniform", "in", "out", "buffer", "layout")
}

internal fun findMatching(tokens: List<GlslToken>, openIndex: Int, open: String, close: String): Int {
    var depth = 0
    for (index in openIndex until tokens.size) {
        when (tokens[index].text) {
            open -> depth++
            close -> {
                depth--
                if (depth == 0) return index
            }
        }
    }
    return -1
}

internal fun splitTopLevel(tokens: List<GlslToken>, delimiter: String): List<List<GlslToken>> {
    val result = mutableListOf<List<GlslToken>>()
    var start = 0
    var paren = 0
    var bracket = 0
    var brace = 0
    tokens.forEachIndexed { index, token ->
        when (token.text) {
            "(" -> paren++
            ")" -> if (paren > 0) paren--
            "[" -> bracket++
            "]" -> if (bracket > 0) bracket--
            "{" -> brace++
            "}" -> if (brace > 0) brace--
            delimiter -> if (paren == 0 && bracket == 0 && brace == 0) {
                result += tokens.subList(start, index)
                start = index + 1
            }
        }
    }
    result += tokens.subList(start, tokens.size)
    return result.filter { it.isNotEmpty() }
}

internal fun collectFunctionStatements(document: GlslDocument, function: GlslFunction): List<GlslStatement> {
    val open = function.bodyOpen ?: return emptyList()
    val close = function.bodyClose ?: return emptyList()
    val bodyTokens = document.significantTokens.filter { it.start > open.start && it.end < close.end }
    val statements = mutableListOf<GlslStatement>()
    var startIndex = 0
    var parenDepth = 0
    var bracketDepth = 0
    var braceDepth = 0

    fun resetStart(next: Int) {
        startIndex = next
        while (startIndex < bodyTokens.size && bodyTokens[startIndex].text == "}") startIndex++
    }

    bodyTokens.forEachIndexed { index, token ->
        when (token.text) {
            "(" -> parenDepth++
            ")" -> if (parenDepth > 0) parenDepth--
            "[" -> bracketDepth++
            "]" -> if (bracketDepth > 0) bracketDepth--
            "{" -> {
                braceDepth++
                resetStart(index + 1)
            }

            "}" -> {
                if (braceDepth > 0) braceDepth--
                resetStart(index + 1)
            }

            ";" -> if (parenDepth == 0 && bracketDepth == 0) {
                val statementTokens = bodyTokens.subList(startIndex, index + 1)
                    .filterNot { it.text == "{" || it.text == "}" }
                if (statementTokens.isNotEmpty()) {
                    statements += GlslStatement(
                        file = document.path,
                        start = statementTokens.first().start,
                        end = statementTokens.last().end,
                        tokens = statementTokens
                    )
                }
                resetStart(index + 1)
            }
        }
    }
    return statements
}
