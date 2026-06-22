package net.spartanb312.grunt.glsl.shader

internal val GLSL_KEYWORDS = setOf(
    "attribute", "const", "uniform", "varying", "buffer", "shared", "coherent", "volatile",
    "restrict", "readonly", "writeonly", "atomic_uint", "layout", "centroid", "flat",
    "smooth", "noperspective", "patch", "sample", "break", "continue", "do", "for",
    "while", "switch", "case", "default", "if", "else", "subroutine", "in", "out",
    "inout", "float", "double", "int", "void", "bool", "true", "false", "invariant",
    "discard", "return", "mat2", "mat3", "mat4", "dmat2", "dmat3", "dmat4", "mat2x2",
    "mat2x3", "mat2x4", "mat3x2", "mat3x3", "mat3x4", "mat4x2", "mat4x3", "mat4x4",
    "vec2", "vec3", "vec4", "ivec2", "ivec3", "ivec4", "bvec2", "bvec3", "bvec4",
    "dvec2", "dvec3", "dvec4", "uint", "uvec2", "uvec3", "uvec4", "lowp", "mediump",
    "highp", "precision", "struct"
)

internal val GLSL_BUILTIN_TYPES = setOf(
    "float", "double", "int", "void", "bool", "mat2", "mat3", "mat4", "dmat2", "dmat3",
    "dmat4", "mat2x2", "mat2x3", "mat2x4", "mat3x2", "mat3x3", "mat3x4", "mat4x2",
    "mat4x3", "mat4x4", "vec2", "vec3", "vec4", "ivec2", "ivec3", "ivec4", "bvec2",
    "bvec3", "bvec4", "dvec2", "dvec3", "dvec4", "uint", "uvec2", "uvec3", "uvec4",
    "sampler1D", "sampler2D", "sampler3D", "samplerCube", "sampler2DShadow",
    "samplerCubeShadow", "sampler2DArray", "sampler2DArrayShadow", "isampler2D",
    "usampler2D", "image1D", "image2D", "image3D", "imageCube", "atomic_uint"
)

internal val GLSL_BUILTIN_NAMES = GLSL_BUILTIN_TYPES + setOf(
    "abs", "acos", "asin", "atan", "ceil", "clamp", "cos", "cross", "degrees", "distance",
    "dot", "exp", "exp2", "floor", "fract", "inversesqrt", "length", "log", "log2",
    "max", "min", "mix", "mod", "normalize", "pow", "radians", "reflect", "refract",
    "round", "sign", "sin", "smoothstep", "sqrt", "step", "tan", "texture", "texture2D",
    "textureLod", "textureProj", "dFdx", "dFdy", "fwidth", "gl_Position", "gl_FragColor",
    "gl_FragCoord", "gl_PointSize", "gl_VertexID", "gl_InstanceID"
)

internal val GLSL_QUALIFIERS = setOf(
    "const", "in", "out", "inout", "uniform", "buffer", "shared", "coherent", "volatile",
    "restrict", "readonly", "writeonly", "centroid", "flat", "smooth", "noperspective",
    "patch", "sample", "invariant", "lowp", "mediump", "highp", "precision"
)

internal val GLSL_CONTROL_KEYWORDS = setOf(
    "if", "for", "while", "do", "switch", "return", "break", "continue", "discard", "else"
)

internal fun isIdentifierStart(ch: Char): Boolean = ch == '_' || ch in 'A'..'Z' || ch in 'a'..'z'

internal fun isIdentifierPart(ch: Char): Boolean = isIdentifierStart(ch) || ch in '0'..'9'

internal fun isPreservedName(name: String, patterns: List<String>): Boolean {
    return patterns.any { pattern ->
        when {
            pattern == name -> true
            pattern.endsWith("*") -> name.startsWith(pattern.dropLast(1))
            pattern.startsWith("*") -> name.endsWith(pattern.drop(1))
            else -> false
        }
    }
}

internal fun normalizeResourcePath(path: String): String {
    return path.replace('\\', '/').removePrefix("/")
}

internal class GlslPathMatcher(
    private val includeRules: List<String>,
    private val excludeRules: List<String>
) {
    fun matches(path: String): Boolean {
        val normalized = normalizeResourcePath(path)
        return includeRules.any { matchRule(it, normalized) } &&
            excludeRules.none { matchRule(it, normalized) }
    }

    private fun matchRule(rule: String, path: String): Boolean {
        val normalizedRule = normalizeResourcePath(rule)
        return when {
            normalizedRule == "**" -> true
            normalizedRule.startsWith("**") -> path.endsWith(normalizedRule.removePrefix("**"))
            normalizedRule.endsWith("/**") -> path.startsWith(normalizedRule.removeSuffix("**"))
            normalizedRule.endsWith("**") -> path.startsWith(normalizedRule.removeSuffix("**"))
            else -> path == normalizedRule
        }
    }
}
