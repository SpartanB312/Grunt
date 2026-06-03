package net.spartanb312.grunt.ir.text

internal sealed interface IrSExpr {
    data class Atom(val value: String) : IrSExpr
    data class StringValue(val value: String) : IrSExpr
    data class ListValue(val items: List<IrSExpr>) : IrSExpr
}

internal class IrSExpressionParser(private val text: String) {
    private var index = 0

    fun parseAll(): List<IrSExpr> {
        val result = mutableListOf<IrSExpr>()
        skipWhitespace()
        while (!eof()) {
            result += parse()
            skipWhitespace()
        }
        return result
    }

    private fun parse(): IrSExpr {
        skipWhitespace()
        return when (peek()) {
            '(' -> parseList()
            '"' -> parseString()
            null -> error("Unexpected end of input")
            else -> parseAtom()
        }
    }

    private fun parseList(): IrSExpr.ListValue {
        expect('(')
        val items = mutableListOf<IrSExpr>()
        skipWhitespace()
        while (peek() != ')') {
            if (eof()) error("Unclosed list")
            items += parse()
            skipWhitespace()
        }
        expect(')')
        return IrSExpr.ListValue(items)
    }

    private fun parseString(): IrSExpr.StringValue {
        expect('"')
        val builder = StringBuilder()
        while (true) {
            val char = next() ?: error("Unclosed string")
            when (char) {
                '"' -> return IrSExpr.StringValue(builder.toString())
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

    private fun parseAtom(): IrSExpr.Atom {
        val start = index
        while (!eof()) {
            val char = peek() ?: break
            if (char.isWhitespace() || char == '(' || char == ')') break
            index++
        }
        if (start == index) error("Expected atom at index $index")
        return IrSExpr.Atom(text.substring(start, index))
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

internal val IrSExpr.headAtom: String?
    get() = (this as? IrSExpr.ListValue)?.items?.firstOrNull()?.atomOrNull()

internal fun IrSExpr.atomOrNull(): String? = (this as? IrSExpr.Atom)?.value

internal fun IrSExpr.stringOrNull(): String? = (this as? IrSExpr.StringValue)?.value

internal fun IrSExpr.asList(): IrSExpr.ListValue {
    return this as? IrSExpr.ListValue ?: error("Expected list, got $this")
}

internal fun IrSExpr.asAtom(): String {
    return atomOrNull() ?: error("Expected atom, got $this")
}

internal fun IrSExpr.asString(): String {
    return stringOrNull() ?: error("Expected string, got $this")
}

internal fun IrSExpr.ListValue.field(name: String): IrSExpr {
    return fieldOrNull(name) ?: error("Missing field '$name' in ${headAtom ?: "list"}")
}

internal fun IrSExpr.ListValue.fieldOrNull(name: String): IrSExpr? {
    for (item in items) {
        val list = item as? IrSExpr.ListValue ?: continue
        if (list.items.firstOrNull()?.atomOrNull() != name) continue
        val rest = list.items.drop(1)
        return when (rest.size) {
            0 -> IrSExpr.ListValue(emptyList())
            1 -> rest.first()
            else -> IrSExpr.ListValue(rest)
        }
    }
    return null
}

internal fun IrSExpr.ListValue.expectHead(name: String): IrSExpr.ListValue {
    val actual = headAtom ?: error("Expected list head '$name', got empty list")
    if (actual != name) error("Expected list head '$name', got '$actual'")
    return this
}
