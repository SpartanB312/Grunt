package net.spartanb312.grunt.process

import net.spartanb312.grunt.config.Configurable
import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.process.resource.ResourceCache

abstract class Transformer(name: String) : Configurable(name) {
    val enabled by value("Enabled", false)
    abstract fun ResourceCache.transform()
}