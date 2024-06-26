package net.spartanb312.grunt.process

import net.spartanb312.grunt.config.Configurable
import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.process.resource.ResourceCache

abstract class Transformer(name: String, val category: Category) : Configurable(name) {
    val enabled by value("Enabled", false)
    abstract fun ResourceCache.transform()
    enum class Category {
        Encryption,
        Controlflow,
        Minecraft,
        Miscellaneous,
        Optimization,
        Redirect,
        Renaming
    }
}