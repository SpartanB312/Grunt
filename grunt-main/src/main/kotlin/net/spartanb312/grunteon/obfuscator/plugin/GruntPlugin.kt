package net.spartanb312.grunteon.obfuscator.plugin

interface GruntPlugin {

    fun onLoad(context: PluginContext) {
    }

    fun onEnable(context: PluginContext)

    fun onDisable(context: PluginContext) {
    }

}
