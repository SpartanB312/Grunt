package net.spartanb312.grunt.glsl.shader

internal class GlslAnalyzer(
    private val documents: List<GlslDocument>
) {
    private val functionNameTokens = documents
        .flatMap { it.functions }
        .mapTo(mutableSetOf()) { FunctionTokenKey(it.file, it.nameToken.start, it.nameToken.end) }

    fun analyze(document: GlslDocument, function: GlslFunction): GlslFunctionAnalysis {
        val root = GlslScope()
        val symbols = mutableListOf<GlslSymbol>()
        val callTokens = mutableListOf<GlslToken>()
        val statements = collectFunctionStatements(document, function)
        val bodyDirectives = directivesInFunction(document, function)
        val directiveIdentifiers = bodyDirectives.flatMapTo(linkedSetOf()) { directive ->
            collectDirectiveIdentifiers(directive.text)
        }
        function.parameters.forEach { parameter ->
            val symbol = GlslSymbol(parameter.name, parameter.nameToken, GlslSymbolKind.Parameter, root)
            root.declare(symbol)
            symbols += symbol
        }

        val scopeStack = ArrayDeque<GlslScope>()
        scopeStack += root
        var statementStart = 0
        val bodyTokens = bodyTokens(document, function)
        var parenDepth = 0
        var bracketDepth = 0

        fun currentScope(): GlslScope = scopeStack.last()

        fun processStatement(endExclusive: Int) {
            val statementTokens = bodyTokens.subList(statementStart, endExclusive)
                .filterNot { it.text == "{" || it.text == "}" }
            if (statementTokens.isNotEmpty()) {
                processStatementTokens(statementTokens, currentScope(), symbols, callTokens)
            }
        }

        bodyTokens.forEachIndexed { index, token ->
            when (token.text) {
                "(" -> parenDepth++
                ")" -> if (parenDepth > 0) parenDepth--
                "[" -> bracketDepth++
                "]" -> if (bracketDepth > 0) bracketDepth--
                "{" -> {
                    if (statementStart < index) processStatement(index)
                    scopeStack += GlslScope(currentScope())
                    statementStart = index + 1
                }

                "}" -> {
                    if (statementStart < index) processStatement(index)
                    if (scopeStack.size > 1) scopeStack.removeLast()
                    statementStart = index + 1
                }

                ";" -> if (parenDepth == 0 && bracketDepth == 0) {
                    processStatement(index + 1)
                    statementStart = index + 1
                }
            }
        }

        return GlslFunctionAnalysis(
            function = function,
            symbols = symbols,
            callTokens = callTokens,
            statements = statements,
            directiveIdentifiers = directiveIdentifiers,
            hasBodyDirectives = bodyDirectives.isNotEmpty()
        )
    }

    fun collectCallTokens(document: GlslDocument): List<GlslToken> {
        return document.significantTokens.mapIndexedNotNull { index, token ->
            if (!token.isIdentifierLike) return@mapIndexedNotNull null
            if (FunctionTokenKey(token.file, token.start, token.end) in functionNameTokens) return@mapIndexedNotNull null
            if (document.significantTokens.getOrNull(index + 1)?.text != "(") return@mapIndexedNotNull null
            if (document.significantTokens.getOrNull(index - 1)?.text == ".") return@mapIndexedNotNull null
            if (token.text in GLSL_CONTROL_KEYWORDS || token.text in GLSL_BUILTIN_NAMES) return@mapIndexedNotNull null
            token
        }
    }

    private fun processStatementTokens(
        tokens: List<GlslToken>,
        scope: GlslScope,
        symbols: MutableList<GlslSymbol>,
        callTokens: MutableList<GlslToken>
    ) {
        val declaration = findDeclaration(tokens)
        val declarationTokens = mutableSetOf<Pair<Int, Int>>()
        val typeTokens = mutableSetOf<Pair<Int, Int>>()
        if (declaration != null) {
            declaration.typeTokens.forEach { typeTokens += it.start to it.end }
            declaration.declarations.forEach { nameToken ->
                val symbol = GlslSymbol(nameToken.text, nameToken, GlslSymbolKind.Local, scope)
                scope.declare(symbol)
                symbols += symbol
                declarationTokens += nameToken.start to nameToken.end
            }
        }

        tokens.forEachIndexed { index, token ->
            if (!token.isIdentifierLike) return@forEachIndexed
            val tokenKey = token.start to token.end
            if (tokenKey in declarationTokens || tokenKey in typeTokens) return@forEachIndexed
            if (tokens.getOrNull(index - 1)?.text == ".") return@forEachIndexed
            val isCall = tokens.getOrNull(index + 1)?.text == "("
            if (isCall) {
                if (token.text !in GLSL_CONTROL_KEYWORDS && token.text !in GLSL_BUILTIN_NAMES) {
                    callTokens += token
                }
                return@forEachIndexed
            }
            scope.lookup(token.text)?.references?.add(token)
        }
    }
}

internal fun collectDirectiveIdentifiers(text: String): Set<String> {
    return DIRECTIVE_IDENTIFIER_REGEX.findAll(text).mapTo(linkedSetOf()) { it.value }
}

internal fun collectDocumentDirectiveIdentifiers(documents: List<GlslDocument>): Set<String> {
    return documents
        .flatMap { it.directives }
        .flatMapTo(linkedSetOf()) { directive -> collectDirectiveIdentifiers(directive.text) }
}

private fun directivesInFunction(document: GlslDocument, function: GlslFunction): List<GlslDirective> {
    val open = function.bodyOpen ?: return emptyList()
    val close = function.bodyClose ?: return emptyList()
    return document.directives.filter { directive ->
        directive.start > open.start && directive.end < close.end
    }
}

private val DIRECTIVE_IDENTIFIER_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*")

private data class FunctionTokenKey(
    val file: ResourcePath,
    val start: Int,
    val end: Int
)

internal fun bodyTokens(document: GlslDocument, function: GlslFunction): List<GlslToken> {
    val open = function.bodyOpen ?: return emptyList()
    val close = function.bodyClose ?: return emptyList()
    return document.significantTokens.filter { it.start > open.start && it.end < close.end }
}

internal data class DeclarationInfo(
    val typeTokens: List<GlslToken>,
    val declarations: List<GlslToken>
)

internal fun findDeclaration(tokens: List<GlslToken>): DeclarationInfo? {
    if (tokens.isEmpty()) return null
    var index = 0
    if (tokens[index].text in GLSL_CONTROL_KEYWORDS) return null
    while (tokens.getOrNull(index)?.text in GLSL_QUALIFIERS) index++
    if (tokens.getOrNull(index)?.text == "layout") {
        val open = tokens.getOrNull(index + 1)
        if (open?.text == "(") {
            val close = findMatching(tokens, index + 1, "(", ")")
            if (close != -1) index = close + 1
        }
        while (tokens.getOrNull(index)?.text in GLSL_QUALIFIERS) index++
    }
    val typeToken = tokens.getOrNull(index) ?: return null
    if (!typeToken.isIdentifierLike) return null
    val next = tokens.getOrNull(index + 1) ?: return null
    if (!next.isIdentifierLike) return null
    if (tokens.getOrNull(index + 2)?.text == "(") return null

    val typeTokens = tokens.subList(0, index + 1)
    val declaratorTokens = tokens.drop(index + 1).dropLastWhile { it.text == ";" }
    val declarations = splitTopLevel(declaratorTokens, ",").mapNotNull { segment ->
        segment.takeWhile { it.text != "=" }
            .firstOrNull { it.kind == GlslTokenKind.Identifier }
    }
    if (declarations.isEmpty()) return null
    return DeclarationInfo(typeTokens, declarations)
}

internal class GlslNameGenerator(
    private val reserved: MutableSet<String>
) {
    private var index = 0

    fun next(): String {
        while (true) {
            val candidate = nameFor(index++)
            if (candidate !in reserved && candidate !in GLSL_KEYWORDS && candidate !in GLSL_BUILTIN_NAMES) {
                reserved += candidate
                return candidate
            }
        }
    }

    private fun nameFor(value: Int): String {
        var length = MIN_IL_NAME_LENGTH
        var offset = value
        while (offset >= capacity(length)) {
            offset -= capacity(length)
            length++
        }
        return buildString(length) {
            repeat(length) { index ->
                val base = IL_NAME_SEED[index % IL_NAME_SEED.length]
                val flip = (offset shr index) and 1
                append(if (flip == 0) base else flipIlChar(base))
            }
        }
    }

    private fun capacity(length: Int): Int {
        return if (length >= 30) Int.MAX_VALUE else 1 shl length
    }

    private fun flipIlChar(char: Char): Char {
        return if (char == 'i') 'l' else 'i'
    }

    private companion object {
        const val MIN_IL_NAME_LENGTH = 7
        const val IL_NAME_SEED = "ilillli"
    }
}
