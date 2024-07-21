package net.spartanb312.grunt.process

import net.spartanb312.grunt.config.Configurable
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.resource.ResourceCache

abstract class Transformer(name: String, val category: Category) : Configurable(name) {
    open val enabled by setting("Enabled", false)
    open var order = 0
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

infix fun Transformer.order(order: Int): Transformer {
    this.order = order
    return this
}