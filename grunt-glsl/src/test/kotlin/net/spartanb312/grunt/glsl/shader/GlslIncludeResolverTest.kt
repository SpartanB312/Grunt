package net.spartanb312.grunt.glsl.shader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GlslIncludeResolverTest {
    @Test
    fun resolvesQuotedIncludesRelativeToCurrentFile() {
        val graph = GlslIncludeResolver(listOf("", "shaders")).resolve(
            mapOf(
                "shaders/main.vert" to """#include "common/math.glsl"""",
                "shaders/common/math.glsl" to "float x;"
            )
        )

        assertEquals(listOf("shaders/common/math.glsl"), graph.edges.getValue("shaders/main.vert"))
    }

    @Test
    fun resolvesAngleIncludesFromRoots() {
        val graph = GlslIncludeResolver(listOf("", "shaders")).resolve(
            mapOf(
                "assets/main.frag" to """#include <common/math.glsl>""",
                "shaders/common/math.glsl" to "float x;"
            )
        )

        assertEquals(listOf("shaders/common/math.glsl"), graph.edges.getValue("assets/main.frag"))
    }

    @Test
    fun warnsAndContinuesForMissingIncludesByDefault() {
        val graph = GlslIncludeResolver(listOf("")).resolve(
            mapOf("main.frag" to """#include "missing.glsl"""")
        )

        assertEquals(emptyList(), graph.edges.getValue("main.frag"))
        assertEquals(listOf("Missing GLSL include 'missing.glsl' from main.frag"), graph.warnings)
    }

    @Test
    fun rejectsMissingIncludesWhenStrictModeIsEnabled() {
        assertFailsWith<GlslObfuscationException> {
            GlslIncludeResolver(listOf(""), failOnMissingIncludes = true).resolve(
                mapOf("main.frag" to """#include "missing.glsl"""")
            )
        }
    }

    @Test
    fun rejectsIncludeCycles() {
        assertFailsWith<GlslObfuscationException> {
            GlslIncludeResolver(listOf("")).resolve(
                mapOf(
                    "a.glsl" to """#include "b.glsl"""",
                    "b.glsl" to """#include "a.glsl""""
                )
            )
        }
    }
}
