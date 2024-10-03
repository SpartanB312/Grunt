package net.spartanb312.grunt.auth.process

import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.encrypt.ConstPoolEncryptTransformer
import net.spartanb312.grunt.utils.logging.Logger

object ConstClassLoaderTransformer : Transformer("ConstClassLoader", Category.Miscellaneous) {

    private val classList get() = ConstPoolEncryptTransformer.generatedClasses

    override fun ResourceCache.transform() {
        val companions = classList.toList()
        classList.clear()
        // Requires ConstPollEncrypt to be enabled
        if (!ConstPoolEncryptTransformer.enabled) {
            Logger.warn("Disabled ConstClassLoader, which requires ConstPoolEncrypt to be enabled!")
            return
        }
    }

}