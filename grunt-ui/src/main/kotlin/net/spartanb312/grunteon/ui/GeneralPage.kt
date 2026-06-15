package net.spartanb312.grunteon.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import net.spartanb312.grunteon.obfuscator.process.ObfConfig

@Composable
fun GeneralPage(
    obsConfigState: MutableState<ObfConfig>,
) {
    var globalConfig by DataClassUpdater(obsConfigState, ObfConfig::globalConfig)
    PanelSurface(
        title = "General Configuration",
        description = "Top-level obfuscation config options.",
        modifier = Modifier.fillMaxSize()
    ) {
        ScrollPanel {
            ConfigEditor(
                value = globalConfig,
                onChange = { globalConfig = it },
            )
        }
    }
}
