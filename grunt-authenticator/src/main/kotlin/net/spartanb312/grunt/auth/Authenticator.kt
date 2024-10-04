package net.spartanb312.grunt.auth

import net.spartanb312.grunt.auth.process.RemoteLoaderTransformer
import net.spartanb312.grunt.event.events.TransformerEvent
import net.spartanb312.grunt.event.listener
import net.spartanb312.grunt.plugin.Plugin
import net.spartanb312.grunt.plugin.PluginManager
import net.spartanb312.grunt.process.Transformers
import net.spartanb312.grunt.process.transformers.misc.HWIDAuthenticatorTransformer
import net.spartanb312.grunt.utils.logging.Logger

/**
 * Remote loading and verification services
 * Working in progress
 */
fun main(args: Array<String>) {
    PluginManager.addInternalPlugin(Authenticator)
    net.spartanb312.grunt.main(arrayOf("alien.json"))
}

object Authenticator : Plugin() {

    private const val VERSION = "1.0.0"

    init {
        listener<TransformerEvent.Before> {
            // Disable the lightweight authenticator if authenticator injector is enabled
            if (it.transformer == HWIDAuthenticatorTransformer && RemoteLoaderTransformer.enabled) it.cancel()
        }
        subscribe()
    }

    override fun onInit() {
        Logger.info("Initializing authenticator $VERSION")
        Transformers.register(RemoteLoaderTransformer, 510) // After const pool encrypt
    }

}