package net.spartanb312.grunt.example

import net.spartanb312.grunt.event.events.TransformerEvent
import net.spartanb312.grunt.event.listener
import net.spartanb312.grunt.plugin.Plugin
import net.spartanb312.grunt.process.transformers.flow.ControlflowTransformer
import net.spartanb312.grunt.utils.logging.Logger

/**
 * Example plugin
 */
object Main : Plugin(
    "Example",
    "1.0.0",
    "B_312",
    "This is an example plugin",
    "2.4.0"
) {

    init {
        listener<TransformerEvent> {
            Logger.info("Running: ${it.transformer.name}")
            if (it.transformer == ControlflowTransformer) {
                it.cancel()
                Logger.info("Disabled control flow!")
            }
        }
        subscribe()
    }

    override fun onInit() {
        Logger.info("Initializing my plugin...")
    }

}