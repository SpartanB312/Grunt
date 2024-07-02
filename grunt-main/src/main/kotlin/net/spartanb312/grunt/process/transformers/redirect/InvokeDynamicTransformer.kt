package net.spartanb312.grunt.process.transformers.redirect

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache

/**
 * Replace invokes to invoke dynamic
 * Coming soon
 */
object InvokeDynamicTransformer : Transformer("InvokeDynamic", Category.Redirect) {

    private val encryptCall by setting("EncryptCall", false)

    override fun ResourceCache.transform() {

    }

}