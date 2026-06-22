package net.spartanb312.grunt.glsl.shader

internal object GlslLexer {
    fun lex(path: ResourcePath, source: String): List<GlslToken> {
        val tokens = mutableListOf<GlslToken>()
        var index = 0
        var line = 1
        var column = 1
        var atLineStart = true
        var onlyWhitespaceOnLine = true
        var conditionalDepth = 0

        fun add(kind: GlslTokenKind, start: Int, end: Int, startLine: Int, startColumn: Int, preprocessor: Boolean = false) {
            tokens += GlslToken(
                kind = kind,
                text = source.substring(start, end),
                file = path,
                start = start,
                end = end,
                line = startLine,
                column = startColumn,
                inPreprocessor = preprocessor,
                conditionalDepth = conditionalDepth
            )
        }

        fun consumeNewline() {
            val start = index
            val startLine = line
            val startColumn = column
            if (source.getOrNull(index) == '\r' && source.getOrNull(index + 1) == '\n') {
                index += 2
            } else {
                index++
            }
            add(GlslTokenKind.NewLine, start, index, startLine, startColumn)
            line++
            column = 1
            atLineStart = true
            onlyWhitespaceOnLine = true
        }

        while (index < source.length) {
            val ch = source[index]
            val start = index
            val startLine = line
            val startColumn = column

            if (ch == '\r' || ch == '\n') {
                consumeNewline()
                continue
            }

            if ((atLineStart || onlyWhitespaceOnLine) && ch == '#') {
                while (index < source.length && source[index] != '\n' && source[index] != '\r') {
                    index++
                    column++
                }
                val directiveText = source.substring(start, index)
                add(GlslTokenKind.Directive, start, index, startLine, startColumn, preprocessor = true)
                val directive = directiveText.trimStart().removePrefix("#").trimStart()
                when {
                    directive.startsWith("if") -> conditionalDepth++
                    directive.startsWith("ifdef") -> conditionalDepth++
                    directive.startsWith("ifndef") -> conditionalDepth++
                    directive.startsWith("endif") -> if (conditionalDepth > 0) conditionalDepth--
                }
                atLineStart = false
                onlyWhitespaceOnLine = false
                continue
            }

            if (ch == ' ' || ch == '\t' || ch == '\u000C') {
                while (index < source.length && source[index] != '\n' && source[index] != '\r' &&
                    (source[index] == ' ' || source[index] == '\t' || source[index] == '\u000C')
                ) {
                    index++
                    column++
                }
                add(GlslTokenKind.Whitespace, start, index, startLine, startColumn)
                atLineStart = false
                continue
            }

            if (ch == '/' && source.getOrNull(index + 1) == '/') {
                index += 2
                column += 2
                while (index < source.length && source[index] != '\n' && source[index] != '\r') {
                    index++
                    column++
                }
                add(GlslTokenKind.Comment, start, index, startLine, startColumn)
                atLineStart = false
                onlyWhitespaceOnLine = false
                continue
            }

            if (ch == '/' && source.getOrNull(index + 1) == '*') {
                index += 2
                column += 2
                while (index < source.length) {
                    if (source[index] == '*' && source.getOrNull(index + 1) == '/') {
                        index += 2
                        column += 2
                        break
                    }
                    if (source[index] == '\r' || source[index] == '\n') {
                        if (source[index] == '\r' && source.getOrNull(index + 1) == '\n') index++
                        index++
                        line++
                        column = 1
                    } else {
                        index++
                        column++
                    }
                }
                add(GlslTokenKind.Comment, start, index, startLine, startColumn)
                atLineStart = false
                onlyWhitespaceOnLine = false
                continue
            }

            if (ch == '"') {
                index++
                column++
                var escaped = false
                while (index < source.length) {
                    val current = source[index]
                    index++
                    column++
                    if (escaped) {
                        escaped = false
                    } else if (current == '\\') {
                        escaped = true
                    } else if (current == '"') {
                        break
                    }
                }
                add(GlslTokenKind.StringLiteral, start, index, startLine, startColumn)
                atLineStart = false
                onlyWhitespaceOnLine = false
                continue
            }

            if (isIdentifierStart(ch)) {
                index++
                column++
                while (index < source.length && isIdentifierPart(source[index])) {
                    index++
                    column++
                }
                val text = source.substring(start, index)
                add(
                    kind = if (text in GLSL_KEYWORDS) GlslTokenKind.Keyword else GlslTokenKind.Identifier,
                    start = start,
                    end = index,
                    startLine = startLine,
                    startColumn = startColumn
                )
                atLineStart = false
                onlyWhitespaceOnLine = false
                continue
            }

            if (ch.isDigit() || (ch == '.' && source.getOrNull(index + 1)?.isDigit() == true)) {
                index++
                column++
                while (index < source.length) {
                    val current = source[index]
                    if (current.isLetterOrDigit() || current == '_' || current == '.') {
                        index++
                        column++
                    } else {
                        break
                    }
                }
                add(GlslTokenKind.Number, start, index, startLine, startColumn)
                atLineStart = false
                onlyWhitespaceOnLine = false
                continue
            }

            index++
            column++
            add(GlslTokenKind.Symbol, start, index, startLine, startColumn)
            atLineStart = false
            onlyWhitespaceOnLine = false
        }

        return tokens
    }
}
