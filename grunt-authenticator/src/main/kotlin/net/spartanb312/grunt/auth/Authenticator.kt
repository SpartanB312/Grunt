package net.spartanb312.grunt.auth

import net.spartanb312.grunt.auth.process.AuthenticatorTransformer
import net.spartanb312.grunt.auth.process.ConstClassLoaderTransformer
import net.spartanb312.grunt.event.events.TransformerEvent
import net.spartanb312.grunt.event.listener
import net.spartanb312.grunt.plugin.Plugin
import net.spartanb312.grunt.process.Transformers
import net.spartanb312.grunt.process.transformers.misc.HWIDAuthenticatorTransformer
import net.spartanb312.grunt.utils.logging.Logger

/**
 * Socket authentication injector
 * Working in progress
 */
object Authenticator : Plugin() {

    private const val VERSION = "1.0.0"

    init {
        listener<TransformerEvent.Before> {
            // Disable the lightweight authenticator if authenticator injector is enabled
            if (it.transformer == HWIDAuthenticatorTransformer && AuthenticatorTransformer.enabled) it.cancel()
        }
        subscribe()
    }

    override fun onInit() {
        Logger.info("Initializing authenticator $VERSION")
        Transformers.register(AuthenticatorTransformer, 110) // Before flow and encrypt
        Transformers.register(ConstClassLoaderTransformer, 510) // After const pool encrypt
    }

}