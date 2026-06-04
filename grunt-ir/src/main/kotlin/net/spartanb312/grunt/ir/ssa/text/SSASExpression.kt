package net.spartanb312.grunt.ir.ssa.text

internal sealed interface SSASExpr {
    data class Atom(val value: String) : SSASExpr
    data class StringValue(val value: String) : SSASExpr
    data class ListValue(val items: List<SSASExpr>) : SSASExpr
}

// S-Express
internal class SSASExpressionParser(private val text: String) {
    private var index = 0

    fun parseAll(): List<SSASExpr> {
        val result = mutableListOf<SSASExpr>()
        skipWhitespace()
        while (!eof()) {
            result += parse()
            skipWhitespace()
        }
        return result
    }

    private fun parse(): SSASExpr {
        skipWhitespace()
        return when (peek()) {
            '(' -> parseList()
            '"' -> parseString()
            null -> error("Unexpected end of input")
            else -> parseAtom()
        }
    }

    private fun parseList(): SSASExpr.ListValue {
        expect('(')
        val items = mutableListOf<SSASExpr>()
        skipWhitespace()
        while (peek() != ')') {
            if (eof()) error("Unclosed list")
            items += parse()
            skipWhitespace()
        }
        expect(')')
        return SSASExpr.ListValue(items)
    }

    private fun parseString(): SSASExpr.StringValue {
        expect('"')
        val builder = StringBuilder()
        while (true) {
            val char = next() ?: error("Unclosed string")
            when (char) {
                '"' -> return SSASExpr.StringValue(builder.toString())
                '\\' -> {
                    val escaped = next() ?: error("Unclosed string escape")
                    builder.append(
                        when (escaped) {
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            '"' -> '"'
                            '\\' -> '\\'
                            else -> escaped
                        }
                    )
                }
                else -> builder.append(char)
            }
        }
    }

    private fun parseAtom(): SSASExpr.Atom {
        val start = index
        while (!eof()) {
            val char = peek() ?: break
            if (char.isWhitespace() || char == '(' || char == ')') break
            index++
        }
        if (start == index) error("Expected atom at index $index")
        return SSASExpr.Atom(text.substring(start, index))
    }

    private fun skipWhitespace() {
        while (!eof() && (peek()?.isWhitespace() == true)) index++
    }

    private fun expect(char: Char) {
        val actual = next()
        if (actual != char) error("Expected '$char' at index ${index - 1}, got '$actual'")
    }

    private fun peek(): Char? = text.getOrNull(index)

    private fun next(): Char? = text.getOrNull(index++)

    private fun eof() = index >= text.length
}

internal val SSASExpr.headAtom: String?
    get() = (this as? SSASExpr.ListValue)?.items?.firstOrNull()?.atomOrNull()

internal fun SSASExpr.atomOrNull(): String? = (this as? SSASExpr.Atom)?.value

internal fun SSASExpr.stringOrNull(): String? = (this as? SSASExpr.StringValue)?.value

internal fun SSASExpr.asList(): SSASExpr.ListValue {
    return this as? SSASExpr.ListValue ?: error("Expected list, got $this")
}

internal fun SSASExpr.asAtom(): String {
    return atomOrNull() ?: error("Expected atom, got $this")
}

internal fun SSASExpr.asString(): String {
    return stringOrNull() ?: error("Expected string, got $this")
}

internal fun SSASExpr.ListValue.field(name: String): SSASExpr {
    return fieldOrNull(name) ?: error("Missing field '$name' in ${headAtom ?: "list"}")
}

internal fun SSASExpr.ListValue.fieldOrNull(name: String): SSASExpr? {
    for (item in items) {
        val list = item as? SSASExpr.ListValue ?: continue
        if (list.items.firstOrNull()?.atomOrNull() != name) continue
        val rest = list.items.drop(1)
        return when (rest.size) {
            0 -> SSASExpr.ListValue(emptyList())
            1 -> rest.first()
            else -> SSASExpr.ListValue(rest)
        }
    }
    return null
}

internal fun SSASExpr.ListValue.expectHead(name: String): SSASExpr.ListValue {
    val actual = headAtom ?: error("Expected list head '$name', got empty list")
    if (actual != name) error("Expected list head '$name', got '$actual'")
    return this
}
