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
        title = uiText(UiText.Page.GeneralTitle),
        description = uiText(UiText.Page.GeneralDescription),
        modifier = Modifier.fillMaxSize()
    ) {
        ScrollPanel {
            ConfigEditor(
                value = globalConfig,
                onChange = { globalConfig = it },
                descriptorBasePath = UiDescriptorPaths.GlobalConfig,
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
        title = uiText(UiText.Page.NativeTitle),
        description = uiText(UiText.Page.NativeDescription),
        modifier = Modifier.fillMaxSize()
    ) {
        ScrollPanel {
            ConfigEditor(
                value = nativePipelineConfig,
                onChange = { nativePipelineConfig = it },
                descriptorBasePath = UiDescriptorPaths.NativePipelineConfig,
            )
        }
    }
}
