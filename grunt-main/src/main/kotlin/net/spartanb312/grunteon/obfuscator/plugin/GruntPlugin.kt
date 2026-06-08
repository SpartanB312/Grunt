package net.spartanb312.grunteon.obfuscator.plugin

interface GruntPlugin {

    val pluginID: String
    val pluginName: String
    val version: String

    fun onLoad(context: PluginContext)

}
