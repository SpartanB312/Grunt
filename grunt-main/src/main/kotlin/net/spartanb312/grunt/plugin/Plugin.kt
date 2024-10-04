package net.spartanb312.grunt.plugin

import net.spartanb312.grunt.event.ListenerOwner

abstract class Plugin(
    override val name: String,
    override val version: String,
    override val author: String,
    override val description: String,
    override val minVersion: String
) : ListenerOwner(), PluginInitializer