package net.spartanb312.grunt.glsl.shader

internal typealias ResourcePath = String

internal data class GlslProcessOptions(
    val includeRoots: List<String>,
    val preserveNames: List<String>,
    val renameLocals: Boolean,
    val renameParameters: Boolean,
    val renamePrivateFunctions: Boolean,
    val inlineEnabled: Boolean,
    val inlineMaxStatements: Int,
    val inlineMaxCallSitesPerFunction: Int,
    val inlineMaxExpansionRatio: Double,
    val removeFullyInlinedPrivateFunctions: Boolean,
    val failOnMissingIncludes: Boolean
)

internal data class GlslProcessResult(
    val files: Map<ResourcePath, String>,
    val stats: GlslStats
)

internal data class GlslStats(
    val scannedFiles: Int = 0,
    val parsedFiles: Int = 0,
    val includeEdges: Int = 0,
    val includeWarnings: List<String> = emptyList(),
    val inlinedCalls: Int = 0,
    val renamedLocalSymbols: Int = 0,
    val renamedPrivateFunctions: Int = 0
)

internal enum class GlslTokenKind {
    Identifier,
    Keyword,
    Number,
    StringLiteral,
    Symbol,
    Directive,
    Comment,
    Whitespace,
    NewLine
}

internal data class GlslToken(
    val kind: GlslTokenKind,
    val text: String,
    val file: ResourcePath,
    val start: Int,
    val end: Int,
    val line: Int,
    val column: Int,
    val inPreprocessor: Boolean,
    val conditionalDepth: Int
) {
    val isIdentifierLike: Boolean
        get() = kind == GlslTokenKind.Identifier || kind == GlslTokenKind.Keyword
}

internal data class GlslDirective(
    val file: ResourcePath,
    val text: String,
    val start: Int,
    val end: Int,
    val conditionalDepth: Int
)

internal data class GlslParameter(
    val name: String,
    val nameToken: GlslToken,
    val typeText: String,
    val qualifiers: Set<String>
) {
    val isOutLike: Boolean
        get() = "out" in qualifiers || "inout" in qualifiers

    val isOpaque: Boolean
        get() = typeText.split(Regex("\\s+")).any { word ->
            word.startsWith("sampler") ||
                word.startsWith("image") ||
                word.startsWith("atomic")
        }
}

internal data class GlslFunction(
    val file: ResourcePath,
    val name: String,
    val nameToken: GlslToken,
    val start: Int,
    val end: Int,
    val parameters: List<GlslParameter>,
    val bodyOpen: GlslToken?,
    val bodyClose: GlslToken?,
    val conditionalDepth: Int
) {
    val hasBody: Boolean
        get() = bodyOpen != null && bodyClose != null
}

internal data class GlslGlobalDeclaration(
    val file: ResourcePath,
    val start: Int,
    val end: Int,
    val names: List<GlslToken>,
    val isPublicApi: Boolean
)

internal data class GlslDocument(
    val path: ResourcePath,
    val source: String,
    val tokens: List<GlslToken>,
    val significantTokens: List<GlslToken>,
    val directives: List<GlslDirective>,
    val globalDeclarations: List<GlslGlobalDeclaration>,
    val functions: List<GlslFunction>
)

internal data class GlslStatement(
    val file: ResourcePath,
    val start: Int,
    val end: Int,
    val tokens: List<GlslToken>
) {
    fun text(source: String): String = source.substring(start, end)
}

internal enum class GlslSymbolKind {
    Parameter,
    Local
}

internal class GlslScope(
    val parent: GlslScope? = null
) {
    val symbols = linkedMapOf<String, MutableList<GlslSymbol>>()

    fun declare(symbol: GlslSymbol) {
        symbols.getOrPut(symbol.name) { mutableListOf() } += symbol
    }

    fun lookup(name: String): GlslSymbol? {
        return symbols[name]?.lastOrNull() ?: parent?.lookup(name)
    }
}

internal data class GlslSymbol(
    val name: String,
    val declaration: GlslToken,
    val kind: GlslSymbolKind,
    val scope: GlslScope,
    val references: MutableList<GlslToken> = mutableListOf()
)

internal data class GlslFunctionAnalysis(
    val function: GlslFunction,
    val symbols: List<GlslSymbol>,
    val callTokens: List<GlslToken>,
    val statements: List<GlslStatement>,
    val directiveIdentifiers: Set<String>,
    val hasBodyDirectives: Boolean
)

internal data class TextPatch(
    val file: ResourcePath,
    val start: Int,
    val end: Int,
    val replacement: String
)

internal class GlslObfuscationException(message: String) : IllegalStateException(message)
