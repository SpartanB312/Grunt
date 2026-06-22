package net.spartanb312.grunt.glsl.transformers

import kotlinx.serialization.Serializable
import net.spartanb312.grunt.glsl.shader.GlslPathMatcher
import net.spartanb312.grunt.glsl.shader.GlslProcessOptions
import net.spartanb312.grunt.glsl.shader.GlslProcessor
import net.spartanb312.grunt.glsl.shader.GlslStats
import net.spartanb312.grunt.glsl.shader.normalizeResourcePath
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.PipelineBuilder
import net.spartanb312.grunteon.obfuscator.process.SettingDesc
import net.spartanb312.grunteon.obfuscator.process.SettingName
import net.spartanb312.grunteon.obfuscator.process.StableLevel
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.post
import net.spartanb312.grunteon.obfuscator.process.seq
import net.spartanb312.grunteon.obfuscator.util.Logger
import java.nio.charset.StandardCharsets
import kotlin.io.path.extension

@Transformer.Stability(StableLevel.Developing)
@Transformer.Description(
    "process.glsl.obfuscator.desc",
    "Obfuscate GLSL shader resources"
)
class GlslObfuscator : Transformer<GlslObfuscator.Config>(
    "GlslObfuscator",
    Category.Other,
) {

    @Serializable
    data class Config(
        @SettingName("Path include")
        @SettingDesc("Shader resource path include rules")
        val pathInclude: List<String> = listOf(
            "**.glsl",
            "**.vert",
            "**.frag",
            "**.vsh",
            "**.fsh",
            "**.geom",
            "**.tesc",
            "**.tese",
            "**.comp",
            "**.csh"
        ),
        @SettingName("Path exclude")
        @SettingDesc("Shader resource path exclude rules")
        val pathExclude: List<String> = listOf("META-INF/**"),
        @SettingName("Include roots")
        @SettingDesc("Roots used to resolve #include <...>")
        val includeRoots: List<String> = listOf("", "shaders", "assets/minecraft/shaders"),
        @SettingName("Preserve names")
        @SettingDesc("Names or wildcard patterns that must not be renamed")
        val preserveNames: List<String> = listOf("main", "gl_*"),
        @SettingName("Rename locals")
        val renameLocals: Boolean = true,
        @SettingName("Rename parameters")
        val renameParameters: Boolean = true,
        @SettingName("Rename private functions")
        val renamePrivateFunctions: Boolean = true,
        @SettingName("Inline")
        val inlineEnabled: Boolean = true,
        @SettingName("Inline max statements")
        val inlineMaxStatements: Int = 8,
        @SettingName("Inline max call sites")
        val inlineMaxCallSitesPerFunction: Int = 16,
        @SettingName("Inline expansion ratio")
        val inlineMaxExpansionRatio: Double = 1.5,
        @SettingName("Remove inlined functions")
        val removeFullyInlinedPrivateFunctions: Boolean = true,
        @SettingName("Fail on missing includes")
        @SettingDesc("Stop obfuscation when a #include target is missing. Disabled logs a warning and continues.")
        val failOnMissingIncludes: Boolean = false
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        var finalStats = GlslStats()
        seq {
            val options = config.toOptions()
            val matcher = GlslPathMatcher(config.pathInclude, config.pathExclude)
            val resourceSet = instance.workRes.inputResourceSet
            val shaderFiles = linkedMapOf<String, String>()
            val entriesByPath = linkedMapOf<String, net.spartanb312.grunteon.obfuscator.process.resource.ResourceSet.ResourceEntry>()

            resourceSet.files()
                .filter { it.extension != "class" }
                .forEach { path ->
                    val entryName = normalizeResourcePath(resourceSet.entryName(path))
                    if (!matcher.matches(entryName)) return@forEach
                    val entry = resourceSet[path].firstOrNull() ?: return@forEach
                    shaderFiles[entryName] = entry.content.toString(StandardCharsets.UTF_8)
                    entriesByPath[entryName] = entry
                }

            if (shaderFiles.isEmpty()) {
                Logger.info(" - GlslObfuscator: no GLSL shader resources matched")
                return@seq
            }

            val result = GlslProcessor(options).process(shaderFiles)
            result.files.forEach { (path, source) ->
                entriesByPath[path]?.content = source.toByteArray(StandardCharsets.UTF_8)
            }
            finalStats = result.stats
        }
        post {
            Logger.info(" - GlslObfuscator:")
            Logger.info("    Scanned ${finalStats.scannedFiles} shader files")
            Logger.info("    Parsed ${finalStats.parsedFiles} shader files")
            Logger.info("    Resolved ${finalStats.includeEdges} include edges")
            finalStats.includeWarnings.forEach { warning ->
                Logger.warn("    $warning")
            }
            Logger.info("    Inlined ${finalStats.inlinedCalls} function calls")
            Logger.info("    Renamed ${finalStats.renamedLocalSymbols} local symbols")
            Logger.info("    Renamed ${finalStats.renamedPrivateFunctions} private functions")
            credit.add(finalStats.scannedFiles * 20L)
            credit.add(finalStats.inlinedCalls * 300L)
            credit.add((finalStats.renamedLocalSymbols + finalStats.renamedPrivateFunctions) * 50L)
        }
    }

    private fun Config.toOptions(): GlslProcessOptions {
        return GlslProcessOptions(
            includeRoots = includeRoots,
            preserveNames = preserveNames,
            renameLocals = renameLocals,
            renameParameters = renameParameters,
            renamePrivateFunctions = renamePrivateFunctions,
            inlineEnabled = inlineEnabled,
            inlineMaxStatements = inlineMaxStatements,
            inlineMaxCallSitesPerFunction = inlineMaxCallSitesPerFunction,
            inlineMaxExpansionRatio = inlineMaxExpansionRatio,
            removeFullyInlinedPrivateFunctions = removeFullyInlinedPrivateFunctions,
            failOnMissingIncludes = failOnMissingIncludes
        )
    }
}
