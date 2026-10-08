package net.spartanb312.grunt.glsl.shader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlslSemanticRegressionTest {
    @Test
    fun crossFileInlineAndRenamePreserveScalarResultsAndArgumentOrder() {
        val files = linkedMapOf(
            "math.glsl" to """
                float blend(float x, float y) {
                    float sum = x + y;
                    return sum * 0.5;
                }
                float scale(float value) {
                    float combined = blend(value + 1.0, value * 3.0);
                    return combined * 2.0;
                }
            """.trimIndent(),
            "main.frag" to """
                #version 330 core
                #include "math.glsl"
                layout(location = 0) out vec4 color;
                float entry(float inputValue) {
                    float result = scale(inputValue);
                    return result - 4.0;
                }
                void main() {
                    color = vec4(entry(2.0));
                }
            """.trimIndent()
        )
        val options = testOptions().copy(preserveNames = listOf("main", "gl_*", "entry"))
        val transformed = GlslProcessor(options).process(files)
        assertEquals(2, transformed.stats.inlinedCalls)
        assertEquals(1, transformed.stats.includeEdges)
        assertTrue(transformed.files.getValue("math.glsl").isBlank())
        assertFalse("blend(" in transformed.files.getValue("main.frag"))
        assertFalse("scale(" in transformed.files.getValue("main.frag"))
        val before = ScalarProgram(files)
        val after = ScalarProgram(transformed.files)
        for (input in listOf(-8.0, -1.0, 0.0, 0.5, 2.0, 16.0)) {
            val expected = input * 4.0 - 3.0
            assertEquals(expected, before.call("entry", listOf(input)))
            assertEquals(expected, after.call("entry", listOf(input)))
        }
        assertEquals(transformed, GlslProcessor(options).process(files))
    }

    // An independent evaluator for this fixture's straight-line scalar GLSL subset, not a GPU compiler.
    private class ScalarProgram(files: Map<String, String>) {
        private val functions = files.flatMap { (path, source) ->
            val document = GlslParser.parse(path, source)
            document.functions.filter { it.hasBody }.map { it.name to (document to it) }
        }.toMap()

        fun call(name: String, arguments: List<Double>): Double {
            val (document, function) = functions.getValue(name)
            assertEquals(function.parameters.size, arguments.size)
            val variables = function.parameters.mapIndexed { index, parameter -> parameter.name to arguments[index] }
                .toMap().toMutableMap()
            for (statement in collectFunctionStatements(document, function)) {
                val tokens = statement.tokens.map { it.text }.dropLast(1)
                if (tokens.first() == "return") return ScalarExpression(tokens.drop(1), variables, ::call).evaluate()
                val equals = tokens.indexOf("=")
                assertTrue(equals > 0)
                val variable = tokens[equals - 1]
                if (tokens.first() == "float") assertFalse(variable in variables, "Duplicate declaration: $variable")
                variables[variable] = ScalarExpression(tokens.drop(equals + 1), variables, ::call).evaluate()
            }
            error("Missing return in $name")
        }
    }

    private class ScalarExpression(
        private val tokens: List<String>,
        private val variables: Map<String, Double>,
        private val call: (String, List<Double>) -> Double
    ) {
        private var index = 0

        fun evaluate(): Double = expression().also { assertEquals(tokens.size, index) }

        private fun expression(): Double {
            var value = term()
            while (tokens.getOrNull(index) in listOf("+", "-")) {
                val operation = tokens[index++]
                val right = term()
                value = if (operation == "+") value + right else value - right
            }
            return value
        }

        private fun term(): Double {
            var value = primary()
            while (tokens.getOrNull(index) in listOf("*", "/")) {
                val operation = tokens[index++]
                val right = primary()
                value = if (operation == "*") value * right else value / right
            }
            return value
        }

        private fun primary(): Double {
            val token = tokens[index++]
            if (token == "-") return -primary()
            if (token == "(") {
                val value = expression()
                assertEquals(")", tokens[index++])
                return value
            }
            token.toDoubleOrNull()?.let { return it }
            if (tokens.getOrNull(index) != "(") return variables.getValue(token)
            index++
            val arguments = mutableListOf<Double>()
            if (tokens[index] != ")") {
                arguments += expression()
                while (tokens[index] == ",") {
                    index++
                    arguments += expression()
                }
            }
            assertEquals(")", tokens[index++])
            return call(token, arguments)
        }
    }
}
