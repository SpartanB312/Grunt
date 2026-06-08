package net.spartanb312.grunteon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
import net.spartanb312.grunteon.obfuscator.process.DecimalRangeVal
import net.spartanb312.grunteon.obfuscator.process.IntRangeVal
import net.spartanb312.grunteon.obfuscator.process.SettingDesc
import net.spartanb312.grunteon.obfuscator.process.SettingName
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

@Composable
fun Inspector(
    node: PipelineNode?,
    definition: TransformerDefinition?,
    onConfigChange: (TransformerConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalUiPalette.current
    PanelSurface(modifier) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text("Inspector", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (node == null) {
                Text("Select a transformer node to edit its Config.", color = palette.muted)
                return@Column
            }
            Text(definition?.label ?: node.config::class.simpleName.orEmpty(), style = MaterialTheme.typography.titleMedium)
            Text(definition?.description ?: node.config::class.qualifiedName.orEmpty(), color = palette.muted)
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ConfigEditor(
                    value = node.config,
                    onChange = { updated -> onConfigChange(updated as TransformerConfig) },
                )
            }
        }
    }
}

@Composable
private fun ConfigEditor(value: Any, onChange: (Any) -> Unit) {
    val constructor = value::class.primaryConstructor
    if (constructor == null) {
        ReadOnlyValue("Unsupported config", value.toString())
        return
    }
    constructor.parameters.forEach { parameter ->
        val property = value::class.memberProperties.firstOrNull { it.name == parameter.name } ?: return@forEach
        val currentValue = property.getter.call(value)
        val label = property.findAnnotation<SettingName>()?.enText ?: parameter.name.orEmpty()
        val description = property.findAnnotation<SettingDesc>()?.enText
        ConfigField(
            label = label,
            description = description,
            value = currentValue,
            property = property,
            onChange = { newValue ->
                onChange(copyWith(value, parameter, newValue))
            }
        )
    }
}

@Composable
private fun ConfigField(
    label: String,
    description: String?,
    value: Any?,
    property: KProperty1<out Any, *>,
    onChange: (Any?) -> Unit,
) {
    val palette = LocalUiPalette.current
    SectionSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = palette.text, fontWeight = FontWeight.SemiBold)
            if (description != null) Text(description, color = palette.muted, style = MaterialTheme.typography.bodySmall)
            when (value) {
                is Boolean -> BooleanField(value, onChange)
                is String -> StringField(value, onChange)
                is Int -> IntField(value, property, onChange)
                is Double -> DoubleField(value, property, onChange)
                is Enum<*> -> EnumField(value, onChange)
                is ClassFilterConfig -> NestedConfigField(value = value, onChange = onChange)
                is List<*> -> ListField(value, onChange)
                null -> ReadOnlyValue("null", "Nullable fields are not editable in this prototype.")
                else -> ReadOnlyValue(value::class.simpleName ?: "Value", value.toString())
            }
        }
    }
}

@Composable
private fun BooleanField(value: Boolean, onChange: (Any?) -> Unit) {
    val palette = LocalUiPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(checked = value, onCheckedChange = { onChange(it) })
        Text(if (value) "Enabled" else "Disabled", color = palette.muted)
    }
}

@Composable
private fun StringField(value: String, onChange: (Any?) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun IntField(value: Int, property: KProperty1<out Any, *>, onChange: (Any?) -> Unit) {
    val range = property.findAnnotation<IntRangeVal>()
    if (range != null) {
        Slider(
            value = value.toFloat().coerceIn(range.min.toFloat(), range.max.toFloat()),
            onValueChange = { onChange(it.toInt().coerceIn(range.min, range.max)) },
            valueRange = range.min.toFloat()..range.max.toFloat(),
            steps = ((range.max - range.min) / range.step - 1).coerceAtLeast(0)
        )
    }
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text -> text.toIntOrNull()?.let { onChange(it) } },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun DoubleField(value: Double, property: KProperty1<out Any, *>, onChange: (Any?) -> Unit) {
    val range = property.findAnnotation<DecimalRangeVal>()
    if (range != null) {
        Slider(
            value = value.toFloat().coerceIn(range.min.toFloat(), range.max.toFloat()),
            onValueChange = { onChange(it.toDouble().coerceIn(range.min, range.max)) },
            valueRange = range.min.toFloat()..range.max.toFloat(),
        )
    }
    OutlinedTextField(
        value = "%.4f".format(value).trimEnd('0').trimEnd('.'),
        onValueChange = { text -> text.toDoubleOrNull()?.let { onChange(it) } },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun EnumField(value: Enum<*>, onChange: (Any?) -> Unit) {
    var expanded by remember(value::class) { mutableStateOf(false) }
    val constants = value::class.java.enumConstants.orEmpty()
    Box {
        UiOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(value.name)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            constants.forEach { constant ->
                DropdownMenuItem(
                    text = { Text(constant.name) },
                    onClick = {
                        expanded = false
                        onChange(constant)
                    }
                )
            }
        }
    }
}

@Composable
private fun NestedConfigField(value: Any, onChange: (Any?) -> Unit) {
    NestedSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            ConfigEditor(value = value, onChange = onChange)
        }
    }
}

@Composable
private fun ListField(value: List<*>, onChange: (Any?) -> Unit) {
    if (value.all { it == null || it is String }) {
        OutlinedTextField(
            value = value.filterIsInstance<String>().joinToString("\n"),
            onValueChange = { text -> onChange(text.lines().filter { it.isNotBlank() }) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 8,
        )
    } else {
        ReadOnlyValue("List", value.joinToString())
    }
}

@Composable
private fun ReadOnlyValue(label: String, text: String) {
    val palette = LocalUiPalette.current
    NestedSurface {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(label, color = palette.muted)
            Text(text, color = palette.text, fontFamily = FontFamily.Monospace)
        }
    }
}
