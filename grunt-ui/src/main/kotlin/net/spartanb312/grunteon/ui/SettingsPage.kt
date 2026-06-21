package net.spartanb312.grunteon.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import net.spartanb312.grunteon.obfuscator.plugin.LoadedPlugin
import net.spartanb312.grunteon.obfuscator.plugin.PluginManager

@Composable
fun SettingsPage(
    appModel: AppModel
) {
    var appConfig by appModel::appConfig
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PanelSurface(
            title = uiText(UiText.Page.AppConfigTitle),
            description = uiText(UiText.Page.AppConfigDescription),
            modifier = Modifier.weight(0.5f)
        ) {
            ScrollPanel {
                ConfigEditor(
                    value = appConfig,
                    onChange = { appConfig = it },
                    descriptorBasePath = UiDescriptorPaths.AppConfig,
                )
            }
        }
        PanelSurface(
            title = uiText(UiText.Page.PluginsTitle),
            description = uiText(UiText.Page.PluginsDescription, "count" to PluginManager.plugins.size),
            modifier = Modifier.weight(0.5f)
        ) {
            ScrollPanel {
                if (PluginManager.plugins.isEmpty()) {
                    FramedSurface(
                        color = FluentTheme.colors.background.card.secondary,
                        modifier = Modifier.fillMaxWidth(),
                        content = {
                            PluginEntry {
                                Text(
                                    uiText(UiText.Plugins.NoPluginsLoaded),
                                    style = FluentTheme.typography.bodyStrong
                                )
                                Text(
                                    uiText(UiText.Plugins.OnlyBuiltInTransformers),
                                    style = FluentTheme.typography.caption,
                                )
                            }
                        })
                } else {
                    PluginManager.plugins.forEach { plugin ->
                        PluginSection(plugin)
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginEntry(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun PluginSection(plugin: LoadedPlugin) {
    val metadata = plugin.metadata
    SectionSurface(Modifier.fillMaxWidth(), content = {
        PluginEntry {
            Text(metadata.name, fontWeight = FontWeight.SemiBold)
            Text(
                uiText(UiText.Plugins.PluginId, "id" to metadata.id),
                style = FluentTheme.typography.bodyStrong
            )
            Text(
                uiText(UiText.Plugins.Version, "version" to metadata.version),
                style = FluentTheme.typography.caption,
            )
            Text(
                uiText(UiText.Plugins.Entry, "entry" to metadata.entryClass),
                fontFamily = FontFamily.Monospace,
            )
            val file = metadata.file.toString()
            if (file.isNotBlank()) {
                Text(
                    file,
                    color = FluentTheme.colors.text.text.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    })
}
