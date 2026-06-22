package net.spartanb312.grunt.glsl

import net.spartanb312.grunt.glsl.transformers.GlslObfuscator
import net.spartanb312.grunteon.obfuscator.plugin.GruntPlugin
import net.spartanb312.grunteon.obfuscator.plugin.PluginContext
import net.spartanb312.grunteon.obfuscator.plugin.registerTransformer

const val GLSL_PLUGIN_ID = "glsl"
const val GLSL_PLUGIN_NAME = "glsl"
const val GLSL_PLUGIN_VERSION = "1.0"

object GlslPlugin : GruntPlugin {
    override val pluginID: String = GLSL_PLUGIN_ID
    override val pluginName: String = GLSL_PLUGIN_NAME
    override val version: String = GLSL_PLUGIN_VERSION

    override fun onLoad(context: PluginContext) {
        context.registerTransformer(
            createTransformer = { GlslObfuscator() },
            createConfig = { GlslObfuscator.Config() }
        )
    }
}
