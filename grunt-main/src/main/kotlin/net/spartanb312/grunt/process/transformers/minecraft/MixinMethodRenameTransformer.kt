package net.spartanb312.grunt.process.transformers.minecraft

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache

/**
 * Renaming mixin classes
 * Last update on 2024/10/26
 */
object MixinMethodRenameTransformer : Transformer("MixinMethodRename", Category.Minecraft) {

    private val dictionary by setting("Dictionary", "Alphabet")
    private val exclusion by setting(
        "Exclusion", listOf(
            "net/spartanb312/Example1",
            "net/spartanb312/Example2.method",
            "net/spartanb312/Example3.method()V",
        )
    )

    override fun ResourceCache.transform() {

    }

}