package net.spartanb312.grunteon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.spartanb312.grunteon.obfuscator.ObfConfig

@Composable
fun GeneralPage(
    config: ObfConfig,
    status: String,
    onConfigChange: (ObfConfig) -> Unit,
    onReload: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalUiPalette.current
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "General",
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.text,
                    fontWeight = FontWeight.Bold
                )
                Text("Top-level ObfConfig options.", color = palette.muted)
            }
            Text(status, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            UiOutlinedButton(onClick = onReload) { Text("Reload") }
            UiButton(onClick = onSave) { Text("Save config") }
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PanelSurface(Modifier.weight(2f).fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        GeneralSection("Input / Output") {
                            PathOption(
                                label = "Input jar",
                                value = config.input,
                                onChange = { onConfigChange(config.copy(input = it)) },
                                onBrowse = { chooseInputPath(config.input)?.toString() },
                                onBrowseDirectory = { chooseInputDirectory(config.input)?.toString() },
                            )
                            PathOption(
                                label = "Output jar",
                                value = config.output.orEmpty(),
                                onChange = {
                                    onConfigChange(config.copy(output = it.ifBlank { null }))
                                },
                                onBrowse = { chooseOutputPath(config.output.orEmpty())?.toString() }
                            )
                            StringListOption("Libraries", config.libs) { onConfigChange(config.copy(libs = it)) }
                        }
                        GeneralSection("Filters") {
                            StringListOption("Global exclusions", config.exclusions) {
                                onConfigChange(config.copy(exclusions = it))
                            }
                            StringListOption("Mixin exclusions", config.mixinExclusions) {
                                onConfigChange(config.copy(mixinExclusions = it))
                            }
                        }
                        GeneralSection("Random / Diagnostics") {
                            BooleanOption("Controllable random", config.controllableRandom) {
                                onConfigChange(config.copy(controllableRandom = it))
                            }
                            StringOption("Input seed", config.inputSeed) { onConfigChange(config.copy(inputSeed = it)) }
                            BooleanOption("Dump mappings", config.dumpMappings) {
                                onConfigChange(config.copy(dumpMappings = it))
                            }
                            BooleanOption("Profiler", config.profiler) { onConfigChange(config.copy(profiler = it)) }
                            BooleanOption("Force compute max", config.forceComputeMax) {
                                onConfigChange(config.copy(forceComputeMax = it))
                            }
                            BooleanOption("Missing dependency check", config.missingCheck) {
                                onConfigChange(config.copy(missingCheck = it))
                            }
                            BooleanOption("Show hidden transformers", config.showHiddenTransformers) {
                                onConfigChange(config.copy(showHiddenTransformers = it))
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        GeneralSection("Jar Output") {
                            BooleanOption("Corrupt headers", config.corruptHeaders) {
                                onConfigChange(config.copy(corruptHeaders = it))
                            }
                            BooleanOption("Corrupt CRC32", config.corruptCRC32) {
                                onConfigChange(config.copy(corruptCRC32 = it))
                            }
                            BooleanOption("Remove time stamps", config.removeTimeStamps) {
                                onConfigChange(config.copy(removeTimeStamps = it))
                            }
                            IntSliderOption("Compression level", config.compressionLevel, 0..9) {
                                onConfigChange(config.copy(compressionLevel = it))
                            }
                            StringOption("Archive comment", config.archiveComment) {
                                onConfigChange(config.copy(archiveComment = it))
                            }
                            StringListOption("Remove file prefixes", config.fileRemovePrefix) {
                                onConfigChange(config.copy(fileRemovePrefix = it))
                            }
                            StringListOption("Remove file suffixes", config.fileRemoveSuffix) {
                                onConfigChange(config.copy(fileRemoveSuffix = it))
                            }
                        }
                        GeneralSection("Dictionary") {
                            StringOption("Custom dictionary file", config.customDictionary) {
                                onConfigChange(config.copy(customDictionary = it))
                            }
                            StringListOption("Custom incremental dictionary", config.customIncrementalDictionary) {
                                onConfigChange(config.copy(customIncrementalDictionary = it))
                            }
                        }
                    }
                }
            }
            GeneralTipsPlaceholder(
                modifier = Modifier.weight(1f).fillMaxSize()
            )
        }
    }
}

@Composable
private fun GeneralTipsPlaceholder(modifier: Modifier = Modifier) {
    val palette = LocalUiPalette.current
    SectionSurface(modifier.fillMaxWidth().heightIn(min = 180.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tips", color = palette.text, fontWeight = FontWeight.SemiBold)
            Text("Reserved for contextual help.", color = palette.muted)
        }
    }
}

@Composable
private fun GeneralSection(title: String, content: @Composable () -> Unit) {
    val palette = LocalUiPalette.current
    SectionSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = palette.text, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun StringOption(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun PathOption(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    onBrowse: suspend () -> String?,
    onBrowseDirectory: (suspend () -> String?)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        UiOutlinedButton(
            onClick = { coroutineScope.launch { onBrowse()?.let(onChange) } },
            modifier = Modifier.width(if (onBrowseDirectory == null) 96.dp else 72.dp)
        ) {
            Text(if (onBrowseDirectory == null) "Browse" else "File")
        }
        if (onBrowseDirectory != null) {
            UiOutlinedButton(
                onClick = { coroutineScope.launch { onBrowseDirectory()?.let(onChange) } },
                modifier = Modifier.width(72.dp)
            ) {
                Text("Dir")
            }
        }
    }
}

@Composable
private fun StringListOption(label: String, value: List<String>, onChange: (List<String>) -> Unit) {
    OutlinedTextField(
        value = value.joinToString("\n"),
        onValueChange = { text -> onChange(text.lines().filter { it.isNotBlank() }) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 8,
    )
}

@Composable
private fun BooleanOption(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalUiPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Checkbox(checked = value, onCheckedChange = onChange)
        Text(label, color = palette.text)
    }
}

@Composable
private fun IntSliderOption(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val palette = LocalUiPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = palette.text)
            Text(value.toString(), color = palette.text)
        }
        Slider(
            value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { onChange(it.toInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0)
        )
    }
}
