package net.spartanb312.grunteon.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.spartanb312.grunteon.obfuscator.process.ObfConfig

@Composable
fun GeneralPage(
    appModel: AppModel
) {
    var globalConfig by DataClassUpdater(appModel::obfConfig, ObfConfig::globalConfig)
    PanelSurface(
        title = "General Configuration",
        description = "Top-level config options.",
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

@Composable
fun NativePage(
    appModel: AppModel
) {
    var nativePipelineConfig by DataClassUpdater(appModel::obfConfig, ObfConfig::nativePipeline)
    PanelSurface(
        title = "Native Configuration",
        description = "Native pipeline config options.",
        modifier = Modifier.fillMaxSize()
    ) {
        ScrollPanel {
            ConfigEditor(
                value = nativePipelineConfig,
                onChange = { nativePipelineConfig = it },
            )
        }
    }
}
