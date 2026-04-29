package net.spartanb312.grunt.yapyap

import net.spartanb312.grunt.yapyap.transformers.encrypt.number.NumberAttributeBasedEncrypt
import net.spartanb312.grunt.yapyap.transformers.encrypt.number.NumberMathematicalEncrypt
import net.spartanb312.grunteon.obfuscator.plugin.GruntPlugin
import net.spartanb312.grunteon.obfuscator.plugin.PluginContext
import net.spartanb312.grunteon.obfuscator.plugin.PluginManager
import net.spartanb312.grunteon.obfuscator.plugin.registerTransformer

/**
 * Grunteon enhancement extension package
 * license: PolyForm Strict License 1.0.0
 */
object Yapyap : GruntPlugin {

    override fun onEnable(context: PluginContext) {
        context.registerTransformer(
            createTransformer = { NumberAttributeBasedEncrypt() },
            createConfig = { NumberAttributeBasedEncrypt.Config() }
        )
        context.registerTransformer(
            createTransformer = { NumberMathematicalEncrypt() },
            createConfig = { NumberMathematicalEncrypt.Config() }
        )
    }

}

fun main(args: Array<String>) {
    val enableUI = args.contains("--enableUI")
    // Run with UI
    if (enableUI) {
        PluginManager.loadPlugin(Yapyap)
        net.spartanb312.grunteon.ui.main(arrayOf("--disablePlugin"))
    } else {
        net.spartanb312.grunteon.obfuscator.main(arrayOf())
    }
}