package net.spartanb312.grunteon.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.*
import net.spartanb312.grunteon.obfuscator.process.*
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
    PanelSurface(modifier) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                "Inspector",
            )
            Spacer(Modifier.height(10.dp))
            if (node == null) {
                Text("Select a transformer node to edit its Config.", color = FluentTheme.colors.text.text.secondary)
                return@Column
            }
            Text(definition?.label ?: node.config::class.simpleName.orEmpty(), style = FluentTheme.typography.subtitle)
            Text(
                definition?.description ?: node.config::class.qualifiedName.orEmpty(),
                color = FluentTheme.colors.text.text.secondary
            )
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
    when (value) {
        is Int -> {
            val range = property.findAnnotation<IntRangeVal>()
            if (range != null) {
                IntSliderField(label, description, value, range, onChange)
            } else {
                InspectorCard(label, description) { IntField(value, onChange) }
            }
        }

        is Double -> {
            val range = property.findAnnotation<DecimalRangeVal>()
            if (range != null) {
                DoubleSliderField(label, description, value, range, onChange)
            } else {
                InspectorCard(label, description) { DoubleField(value, onChange) }
            }
        }

        is ClassFilterConfig -> NestedConfigField(label, description, value, onChange)
        is Boolean -> InspectorCard(label, description) { BooleanField(value, onChange) }
        is String -> InspectorCard(label, description) { StringField(value, onChange) }
        is Enum<*> -> InspectorCard(label, description) { EnumField(value, onChange) }
        is List<*> -> InspectorCard(label, description) { ListField(value, onChange) }
        null -> InspectorCard(label, description) {
            ReadOnlyValue(
                "null",
                "Nullable fields are not editable in this prototype."
            )
        }
        else -> InspectorCard(label, description) {
            ReadOnlyValue(
                value::class.simpleName ?: "Value",
                value.toString()
            )
        }
    }
}

@Composable
private fun InspectorCard(
    label: String,
    description: String?,
    content: @Composable () -> Unit,
) {
    CardExpanderItem(heading = { Text(label, fontWeight = FontWeight.SemiBold) }, icon = null) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (description != null) {
                Text(
                    description,
                    color = FluentTheme.colors.text.text.secondary,
                    style = FluentTheme.typography.caption
                )
            }
            content()
        }
    }
}

@Composable
private fun BooleanField(value: Boolean, onChange: (Any?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UiSwitch(checked = value, onCheckedChange = { onChange(it) })
        Text(if (value) "Enabled" else "Disabled", color = FluentTheme.colors.text.text.secondary)
    }
}

@Composable
private fun StringField(value: String, onChange: (Any?) -> Unit) {
    UiTextField(
        value = value,
        onValueChange = { onChange(it) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun IntField(value: Int, onChange: (Any?) -> Unit) {
    UiTextField(
        value = value.toString(),
        onValueChange = { text -> text.toIntOrNull()?.let { onChange(it) } },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun DoubleField(value: Double, onChange: (Any?) -> Unit) {
    UiTextField(
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
                    onClick = {
                        expanded = false
                        onChange(constant)
                    }
                ) { Text(constant.name) }
            }
        }
    }
}

@Composable
private fun IntSliderField(
    label: String,
    description: String?,
    value: Int,
    range: IntRangeVal,
    onChange: (Any?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var typedValue by remember(value) { mutableStateOf(value.toString()) }
    Expander(
        expanded = expanded,
        onExpandedChanged = { expanded = it },
        heading = { Text(label, fontWeight = FontWeight.SemiBold) },
        icon = null,
        trailing = {
            UiTextField(
                value = typedValue,
                onValueChange = { text ->
                    typedValue = text
                    text.toIntOrNull()?.let { onChange(it.coerceIn(range.min, range.max)) }
                },
                modifier = Modifier.width(112.dp),
                singleLine = true,
            )
        }
    ) {
        if (description != null) {
            Text(
                description,
                color = FluentTheme.colors.text.text.secondary,
                style = FluentTheme.typography.caption,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        CardExpanderItem(heading = {}, icon = null) {
            UiSlider(
                value = value.toFloat().coerceIn(range.min.toFloat(), range.max.toFloat()),
                onValueChange = { onChange(it.toInt().coerceIn(range.min, range.max)) },
                valueRange = range.min.toFloat()..range.max.toFloat(),
                steps = ((range.max - range.min) / range.step - 1).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DoubleSliderField(
    label: String,
    description: String?,
    value: Double,
    range: DecimalRangeVal,
    onChange: (Any?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var typedValue by remember(value) { mutableStateOf("%.4f".format(value).trimEnd('0').trimEnd('.')) }
    Expander(
        expanded = expanded,
        onExpandedChanged = { expanded = it },
        heading = { Text(label, fontWeight = FontWeight.SemiBold) },
        icon = null,
        trailing = {
            UiTextField(
                value = typedValue,
                onValueChange = { text ->
                    typedValue = text
                    text.toDoubleOrNull()?.let { onChange(it.coerceIn(range.min, range.max)) }
                },
                modifier = Modifier.width(112.dp),
                singleLine = true,
            )
        }
    ) {
        if (description != null) {
            Text(
                description,
                color = FluentTheme.colors.text.text.secondary,
                style = FluentTheme.typography.caption,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        CardExpanderItem(heading = {}, icon = null) {
            UiSlider(
                value = value.toFloat().coerceIn(range.min.toFloat(), range.max.toFloat()),
                onValueChange = { onChange(it.toDouble().coerceIn(range.min, range.max)) },
                valueRange = range.min.toFloat()..range.max.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun NestedConfigField(label: String, description: String?, value: Any, onChange: (Any?) -> Unit) {
    CardExpanderItem(
        heading = {
            var expanded by remember { mutableStateOf(false) }
            Expander(
                expanded = expanded,
                onExpandedChanged = { expanded = it },
                heading = { Text(label, fontWeight = FontWeight.SemiBold) },
                icon = null,
                modifier = Modifier.padding(end = 16.dp)
            ) {
                if (description != null) {
                    Text(
                        description,
                        color = FluentTheme.colors.text.text.secondary,
                        style = FluentTheme.typography.caption,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                Column(
                    Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConfigEditor(value = value, onChange = onChange)
                }
            }
        },
        icon = null
    ) {
        if (description != null) {
            Text(description, color = FluentTheme.colors.text.text.secondary, style = FluentTheme.typography.caption)
        }
    }
}

@Composable
private fun ListField(value: List<*>, onChange: (Any?) -> Unit) {
    if (value.all { it == null || it is String }) {
        UiTextField(
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
    NestedSurface {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(label, color = FluentTheme.colors.text.text.secondary)
            Text(text, color = FluentTheme.colors.text.text.primary, fontFamily = FontFamily.Monospace)
        }
    }
}
