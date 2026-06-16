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
            title = "App Configuration",
            description = "Configure Grunteon GUI behavior and appearance.",
            modifier = Modifier.weight(0.5f)
        ) {
            ScrollPanel {
                ConfigEditor(
                    value = appConfig,
                    onChange = { appConfig = it },
                )
            }
        }
        PanelSurface(
            title = "Plugins",
            description = "Loaded ${PluginManager.plugins.size} plugin(s).",
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
                                    "No plugins loaded",
                                    style = FluentTheme.typography.bodyStrong
                                )
                                Text(
                                    "Only built-in transformers are available.",
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
                "Plugin ID: ${metadata.id}",
                style = FluentTheme.typography.bodyStrong
            )
            Text(
                "Version: ${metadata.version} ",
                style = FluentTheme.typography.caption,
            )
            Text(
                "Entry: ${metadata.entryClass}",
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