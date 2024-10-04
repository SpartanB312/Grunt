package net.spartanb312.grunt.plugin

interface PluginInitializer {
    val name: String
    val version: String
    val author: String
    val description: String
    val minVersion: String
    fun onInit()
}