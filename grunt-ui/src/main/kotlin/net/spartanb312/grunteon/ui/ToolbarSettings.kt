package net.spartanb312.grunteon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spartanb312.grunteon.obfuscator.SUBTITLE
import net.spartanb312.grunteon.obfuscator.VERSION
import net.spartanb312.grunteon.obfuscator.plugin.LoadedPlugin
import java.nio.file.Path as NioPath

@Composable
fun TopToolbar(
    page: AppPage,
    onPageChange: (AppPage) -> Unit,
    fontScale: Float,
) {
    val palette = LocalUiPalette.current
    PanelSurface(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Grunteon", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            ToolbarTab(
                label = "General",
                selected = page == AppPage.General,
                onClick = { onPageChange(AppPage.General) }
            )
            ToolbarTab(
                label = "Pipeline Editor",
                selected = page == AppPage.Editor,
                onClick = { onPageChange(AppPage.Editor) }
            )
            ToolbarTab(
                label = "Obfuscation",
                selected = page == AppPage.Obfuscation,
                onClick = { onPageChange(AppPage.Obfuscation) }
            )
            ToolbarTab(
                label = "Settings",
                selected = page == AppPage.Settings,
                onClick = { onPageChange(AppPage.Settings) }
            )
            Spacer(Modifier.weight(1f))
            Text("$VERSION [${SUBTITLE}]", color = palette.muted)
        }
    }
}

@Composable
private fun ToolbarTab(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        UiButton(onClick = onClick) { Text(label) }
    } else {
        UiOutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
fun SettingsPage(
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    uiLogLevel: UiLogLevel,
    onUiLogLevelChange: (UiLogLevel) -> Unit,
    configPath: NioPath,
    status: String,
    plugins: List<LoadedPlugin>,
    modifier: Modifier = Modifier,
) {
    val palette = LocalUiPalette.current
    val settingsScroll = rememberScrollState()
    val pluginsScroll = rememberScrollState()
    PanelSurface(modifier) {
        Row(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(settingsScroll),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Workspace preferences for the editor prototype.", color = palette.muted)
                }
                SettingsSection {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Font Size", fontWeight = FontWeight.SemiBold)
                                Text("Scale all editor text without changing the pipeline data.", color = palette.muted)
                            }
                            Text("${"%.0f".format(fontScale * 100)}%", fontFamily = FontFamily.Monospace)
                        }
                        Slider(
                            value = fontScale,
                            onValueChange = { onFontScaleChange(it.coerceIn(0.8f, 1.3f)) },
                            valueRange = 0.8f..1.3f,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UiOutlinedButton(onClick = { onFontScaleChange(0.9f) }) { Text("Small") }
                            UiOutlinedButton(onClick = { onFontScaleChange(DefaultFontScale) }) { Text("Default") }
                            UiOutlinedButton(onClick = { onFontScaleChange(1.15f) }) { Text("Large") }
                        }
                    }
                }
                SettingsSection {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Obfuscation Log Level", fontWeight = FontWeight.SemiBold)
                            Text("Minimum logger level shown in the Obfuscation console.", color = palette.muted)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (uiLogLevel == UiLogLevel.Info) {
                                UiButton(onClick = { onUiLogLevelChange(UiLogLevel.Info) }) { Text("INFO") }
                            } else {
                                UiOutlinedButton(onClick = { onUiLogLevelChange(UiLogLevel.Info) }) { Text("INFO") }
                            }
                            if (uiLogLevel == UiLogLevel.Debug) {
                                UiButton(onClick = { onUiLogLevelChange(UiLogLevel.Debug) }) { Text("DEBUG") }
                            } else {
                                UiOutlinedButton(onClick = { onUiLogLevelChange(UiLogLevel.Debug) }) { Text("DEBUG") }
                            }
                        }
                    }
                }
                SettingsSection {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Theme", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (themeMode == ThemeMode.Dark) {
                                UiButton(onClick = { onThemeModeChange(ThemeMode.Dark) }) { Text("Dark") }
                            } else {
                                UiOutlinedButton(onClick = { onThemeModeChange(ThemeMode.Dark) }) { Text("Dark") }
                            }
                            if (themeMode == ThemeMode.Light) {
                                UiButton(onClick = { onThemeModeChange(ThemeMode.Light) }) { Text("Light") }
                            } else {
                                UiOutlinedButton(onClick = { onThemeModeChange(ThemeMode.Light) }) { Text("Light") }
                            }
                        }
                    }
                }
                SettingsSection {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Config", fontWeight = FontWeight.SemiBold)
                        Text(
                            configPath.toAbsolutePath().normalize().toString(),
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(status, color = palette.muted)
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(pluginsScroll),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Plugins", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${plugins.size} loaded plugin(s).", color = palette.muted)
                }
                if (plugins.isEmpty()) {
                    SettingsSection {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No plugins loaded", fontWeight = FontWeight.SemiBold)
                            Text("Only built-in transformers are available.", color = palette.muted)
                        }
                    }
                } else {
                    plugins.forEach { plugin ->
                        PluginSection(plugin)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(content: @Composable () -> Unit) {
    SectionSurface(Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun PluginSection(plugin: LoadedPlugin) {
    val palette = LocalUiPalette.current
    val metadata = plugin.metadata
    SettingsSection {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(metadata.name, fontWeight = FontWeight.SemiBold)
            Text(
                "Plugin ID: ${metadata.id}",
                color = palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Version: ${metadata.version} ",
                color = palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Entry: ${metadata.entryClass}",
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val file = metadata.file.toString()
            if (file.isNotBlank()) {
                Text(file, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
