package net.spartanb312.grunt.glsl.shader

internal fun testOptions(
    inlineEnabled: Boolean = true,
    renamePrivateFunctions: Boolean = true,
    renameLocals: Boolean = true,
    renameParameters: Boolean = true
): GlslProcessOptions {
    return GlslProcessOptions(
        includeRoots = listOf("", "shaders", "assets/minecraft/shaders"),
        preserveNames = listOf("main", "gl_*"),
        renameLocals = renameLocals,
        renameParameters = renameParameters,
        renamePrivateFunctions = renamePrivateFunctions,
        inlineEnabled = inlineEnabled,
        inlineMaxStatements = 8,
        inlineMaxCallSitesPerFunction = 16,
        inlineMaxExpansionRatio = 10.0,
        removeFullyInlinedPrivateFunctions = true,
        failOnMissingIncludes = false
    )
}
