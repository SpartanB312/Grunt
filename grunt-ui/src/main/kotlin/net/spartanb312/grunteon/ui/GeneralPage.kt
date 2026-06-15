package net.spartanb312.grunteon.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import kotlinx.coroutines.launch
import net.spartanb312.grunteon.obfuscator.ObfConfig

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

@Composable
private fun GeneralSection(title: String, content: @Composable () -> Unit) {
    SectionSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = FluentTheme.colors.text.text.primary, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun StringOption(label: String, value: String, onChange: (String) -> Unit) {
    UiTextField(
        value = value,
        onValueChange = onChange,
        label = label,
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
        UiTextField(
            value = value,
            onValueChange = onChange,
            label = label,
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
    UiTextField(
        value = value.joinToString("\n"),
        onValueChange = { text -> onChange(text.lines().filter { it.isNotBlank() }) },
        label = label,
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 8,
    )
}

@Composable
private fun BooleanOption(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        UiCheckbox(checked = value, onCheckedChange = onChange)
        Text(label, color = FluentTheme.colors.text.text.primary)
    }
}

@Composable
private fun IntSliderOption(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = FluentTheme.colors.text.text.primary)
            Text(value.toString(), color = FluentTheme.colors.text.text.primary)
        }
        UiSlider(
            value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { onChange(it.toInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0)
        )
    }
}
