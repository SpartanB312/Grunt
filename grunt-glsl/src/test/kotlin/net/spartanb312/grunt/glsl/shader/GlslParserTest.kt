package net.spartanb312.grunt.glsl.shader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GlslParserTest {
    @Test
    fun parsesDirectivesFunctionsParametersAndStatements() {
        val source = """
            #version 330
            #include "common.glsl"
            layout(location = 0) in vec3 position;
            uniform mat4 u_matrix;

            float helper(in float x, float y) {
                float t = x + y;
                return t;
            }
        """.trimIndent()

        val document = GlslParser.parse("shaders/main.vert", source)
        assertEquals(2, document.directives.size)
        assertEquals(listOf("position", "u_matrix"), document.globalDeclarations.flatMap { it.names }.map { it.text })
        assertTrue(document.globalDeclarations.all { it.isPublicApi })
        val helper = document.functions.single()
        assertEquals("helper", helper.name)
        assertEquals(listOf("x", "y"), helper.parameters.map { it.name })
        assertEquals(listOf("float", "float"), helper.parameters.map { it.typeText })
        assertNotNull(helper.bodyOpen)
        assertEquals(2, collectFunctionStatements(document, helper).size)
    }

    @Test
    fun analyzerBindsLocalShadowingInsideFunctionScope() {
        val source = """
            float helper(float x) {
                float value = x;
                {
                    float value = x + 1.0;
                    value = value + 1.0;
                }
                return value;
            }
        """.trimIndent()
        val document = GlslParser.parse("a.glsl", source)
        val analysis = GlslAnalyzer(listOf(document)).analyze(document, document.functions.single())
        val localValues = analysis.symbols.filter { it.name == "value" }
        assertEquals(2, localValues.size)
        assertTrue(localValues.all { it.references.isNotEmpty() })
    }
}
