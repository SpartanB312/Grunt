package net.spartanb312.grunt.yapyap

import net.spartanb312.grunt.yapyap.annotation.ABE_EXTERNAL_CLASS
import net.spartanb312.grunt.yapyap.annotation.ABE_NUMBER_POOL_CLASS
import net.spartanb312.grunt.yapyap.annotation.ABE_STRING_POOL_CLASS
import net.spartanb312.grunt.yapyap.annotation.DISABLE_NUMBER_ABE
import net.spartanb312.grunt.yapyap.annotation.DISABLE_STRING_ABE
import net.spartanb312.grunt.yapyap.transformers.encrypt.number.NumberAttributeBasedEncrypt
import net.spartanb312.grunt.yapyap.transformers.encrypt.number.NumberSPECKEncrypt
import net.spartanb312.grunt.yapyap.transformers.encrypt.string.StringAttributeBasedEncrypt
import net.spartanb312.grunteon.obfuscator.plugin.GruntPlugin
import net.spartanb312.grunteon.obfuscator.plugin.PluginContext
import net.spartanb312.grunteon.obfuscator.plugin.PluginManager
import net.spartanb312.grunteon.obfuscator.plugin.registerTransformer
import net.spartanb312.grunteon.obfuscator.util.DISABLER
import net.spartanb312.grunteon.obfuscator.util.INTERNAL

/**
 * Grunteon enhancement extension package
 * license: PolyForm Strict License 1.0.0
 */

const val PLUGIN_NAME = "Yapyap"
const val PLUGIN_ID = "grunt-yapyap"
const val PLUGIN_VERSION = "1.0"

object Yapyap : GruntPlugin {

    override val pluginID: String = PLUGIN_ID
    override val pluginName: String = PLUGIN_NAME
    override val version: String = PLUGIN_VERSION

    override fun onLoad(context: PluginContext) {
        DISABLER.add(DISABLE_NUMBER_ABE)
        DISABLER.add(DISABLE_STRING_ABE)
        INTERNAL.add(ABE_EXTERNAL_CLASS)
        INTERNAL.add(ABE_NUMBER_POOL_CLASS)
        INTERNAL.add(ABE_STRING_POOL_CLASS)
        context.registerTransformer(
            createTransformer = { NumberAttributeBasedEncrypt() },
            createConfig = { NumberAttributeBasedEncrypt.Config() }
        )
        context.registerTransformer(
            createTransformer = { NumberSPECKEncrypt() },
            createConfig = { NumberSPECKEncrypt.Config() }
        )
        context.registerTransformer(
            createTransformer = { StringAttributeBasedEncrypt() },
            createConfig = { StringAttributeBasedEncrypt.Config() }
        )
    }

}

fun main(args: Array<String>) {
    val enableUI = args.contains("--enableUI")
    // Run with UI
    if (enableUI) {
        PluginManager.loadPlugin(Yapyap)
        PluginManager.freeze()
        net.spartanb312.grunteon.ui.main(arrayOf("--disablePlugin"))
    } else {
        net.spartanb312.grunteon.obfuscator.main(arrayOf())
    }
}
