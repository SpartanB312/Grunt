package net.spartanb312.grunt.glsl.shader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlslProcessorTest {
    @Test
    fun inlinesSimplePrivateHelperAndPreservesPublicApi() {
        val source = """
            #version 330
            uniform mat4 u_matrix;

            float helper(float x, float y) {
                float t = x + y;
                t = t * 0.5;
                return t;
            }

            void main() {
                float t = 1.0;
                float z = helper(t, 2.0);
                gl_Position = u_matrix * vec4(z);
            }
        """.trimIndent()

        val result = GlslProcessor(testOptions()).process(mapOf("shaders/main.vert" to source))
        val output = result.files.getValue("shaders/main.vert")

        assertFalse("helper(" in output)
        assertContains(output, "void main()")
        assertContains(output, "uniform mat4 u_matrix")
        assertContains(output, "gl_Position")
        assertTrue(result.stats.inlinedCalls >= 1)
        assertTrue(result.stats.renamedLocalSymbols >= 1)
    }

    @Test
    fun renamesPrivateFunctionWhenInlineIsDisabled() {
        val source = """
            float helper(float x) {
                return x;
            }

            void main() {
                float z = helper(1.0);
                gl_Position = vec4(z);
            }
        """.trimIndent()

        val result = GlslProcessor(testOptions(inlineEnabled = false)).process(mapOf("main.vert" to source))
        val output = result.files.getValue("main.vert")

        assertFalse("helper" in output)
        assertContains(output, "void main()")
        assertEquals(1, result.stats.renamedPrivateFunctions)
    }

    @Test
    fun skipsUnsafeInlineButStillRenamesSafeLocals() {
        val source = """
            float helper(float x) {
                if (x > 0.0) {
                    return x;
                }
                return 0.0;
            }

            void main() {
                float z = helper(1.0);
                gl_Position = vec4(z);
            }
        """.trimIndent()

        val result = GlslProcessor(testOptions()).process(mapOf("main.vert" to source))
        assertEquals(0, result.stats.inlinedCalls)
        assertTrue(result.stats.renamedLocalSymbols >= 1)
    }

    @Test
    fun nestedInlineDoesNotDeleteInnerHelperBeforeOuterReplacementIsReparsed() {
        val source = """
            float inner(float x) {
                return x + 1.0;
            }

            float outer(float x) {
                return inner(x) * 2.0;
            }

            void main() {
                float z = outer(1.0);
                gl_Position = vec4(z);
            }
        """.trimIndent()

        val result = GlslProcessor(testOptions()).process(mapOf("main.vert" to source))
        val output = result.files.getValue("main.vert")

        assertFalse("inner(" in output)
        assertFalse("outer(" in output)
        assertContains(output, "gl_Position")
    }

    @Test
    fun namesReferencedByFunctionLocalDirectivesAreNotRenamed() {
        val source = """
            bool sampleLayer(int layerIndex, int texelPos) {
                #define TEXEL_Y_OFFSET (1 + layerIndex * 8)
                int y = texelPos + TEXEL_Y_OFFSET;
                return y > 0;
            }

            void main() {
                bool ok = sampleLayer(1, 2);
                gl_Position = vec4(ok ? 1.0 : 0.0);
            }
        """.trimIndent()

        val result = GlslProcessor(testOptions(inlineEnabled = false)).process(mapOf("main.vert" to source))
        val output = result.files.getValue("main.vert")

        assertContains(output, "int layerIndex")
        assertContains(output, "#define TEXEL_Y_OFFSET (1 + layerIndex * 8)")
    }

    @Test
    fun functionsWithConditionalLocalDeclarationsAreNotRenamed() {
        val source = """
            float waveHeight(float pos) {
                #ifndef SETTING_SCREENSHOT_MODE
                float time = frameTimeCounter;
                #else
                float time = 13.37;
                #endif
                return time + pos;
            }
        """.trimIndent()

        val result = GlslProcessor(testOptions(inlineEnabled = false)).process(mapOf("main.frag" to source))
        val output = result.files.getValue("main.frag")

        assertContains(output, "float pos")
        assertContains(output, "float time = frameTimeCounter;")
        assertContains(output, "float time = 13.37;")
        assertContains(output, "return time + pos;")
    }

    @Test
    fun generatedNamesUseIlStyle() {
        val source = """
            float helper(float value) {
                float lower = value * 2.0;
                float higher = lower + 1.0;
                return higher;
            }
        """.trimIndent()

        val result = GlslProcessor(testOptions(inlineEnabled = false)).process(mapOf("main.frag" to source))
        val output = result.files.getValue("main.frag")

        assertContains(output, "float ilillli")
        assertTrue(Regex("\\b[il]{7,}\\b").containsMatchIn(output))
        assertFalse(Regex("\\bfb\\b").containsMatchIn(output))
    }

    @Test
    fun processesMultiFileShaderPackWithIncludes() {
        val result = GlslProcessor(testOptions()).process(
            mapOf(
                "shaders/main.frag" to """
                    #include "common/math.glsl"

                    void main() {
                        float z = helper(1.0, 3.0);
                        gl_FragColor = vec4(z);
                    }
                """.trimIndent(),
                "shaders/common/math.glsl" to """
                    float helper(float x, float y) {
                        float t = x + y;
                        return t * 0.5;
                    }
                """.trimIndent()
            )
        )

        assertEquals(1, result.stats.includeEdges)
        val main = result.files.getValue("shaders/main.frag")
        assertFalse("helper(" in main)
        assertContains(main, "1.0")
        assertContains(main, "3.0")
        assertFalse("helper" in result.files.getValue("shaders/common/math.glsl"))
    }

    @Test
    fun missingIncludesWarnButDoNotStopProcessing() {
        val result = GlslProcessor(testOptions()).process(
            mapOf(
                "shaders/main.frag" to """
                    #include "engine/runtime.glsl"

                    void main() {
                        float value = 1.0;
                        gl_FragColor = vec4(value);
                    }
                """.trimIndent()
            )
        )

        assertEquals(listOf("Missing GLSL include 'engine/runtime.glsl' from shaders/main.frag"), result.stats.includeWarnings)
        assertTrue(result.stats.renamedLocalSymbols >= 1)
        assertContains(result.files.getValue("shaders/main.frag"), "#include \"engine/runtime.glsl\"")
    }

    @Test
    fun defaultTransformerConfigIncludesCshShaders() {
        val matcher = GlslPathMatcher(
            net.spartanb312.grunt.glsl.transformers.GlslObfuscator.Config().pathInclude,
            net.spartanb312.grunt.glsl.transformers.GlslObfuscator.Config().pathExclude
        )

        assertTrue(matcher.matches("shaders/compute.csh"))
    }
}
