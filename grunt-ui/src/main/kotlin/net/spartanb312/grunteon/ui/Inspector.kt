package net.spartanb312.grunteon.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.*
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Decimal
import java.math.RoundingMode
import kotlin.math.max
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

@Composable
fun Inspector(
    node: PipelineNode?,
    definition: TransformerDefinition?,
    onConfigChange: (TransformerConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val transformerName = definition?.label ?: node?.config?.let { it::class.simpleName }
    val transformerDesc = definition?.description ?: node?.config?.let { it::class.qualifiedName }
    PanelSurface(
        if (transformerName == null) "Inspector" else "Inspector - $transformerName",
        transformerDesc ?: "Select a transformer node to edit its Config.",
        modifier
    ) {
        if (node == null) return@PanelSurface
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            ConfigEditor(
                value = node.config,
                onChange = { updated -> onConfigChange(updated as TransformerConfig) },
            )
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
        ConfigField(value, { newValue -> onChange(copyWith(value, parameter, newValue)) }, parameter)
    }
}

@Composable
private fun ConfigField(value: Any, onChange: (Any?) -> Unit, parameter: KParameter) {
    val property = value::class.memberProperties.firstOrNull { it.name == parameter.name } ?: return
    val currentValue = property.getter.call(value)
    val label = property.findAnnotation<SettingName>()?.enText ?: parameter.name.orEmpty()
    val description = property.findAnnotation<SettingDesc>()?.enText
    when (currentValue) {
        is Int -> {
            val range = property.findAnnotation<IntRangeVal>()
            if (range != null) {
                IntSliderField(
                    label = label,
                    description = description,
                    value = currentValue,
                    range = range,
                    onValueChange = onChange
                )
            } else {
                InspectorCard(label = label, description = description) {
                    IntField(
                        value = currentValue,
                        onChange = onChange
                    )
                }
            }
        }

        is Decimal -> {
            val range = property.findAnnotation<DecimalRangeVal>()
            if (range != null) {
                DecimalSliderField(
                    label = label,
                    description = description,
                    value = currentValue,
                    range = range,
                    onValueChange = onChange
                )
            } else {
                InspectorCard(label = label, description = description) {
                    DecimalField(
                        value = currentValue,
                        onChange = onChange
                    )
                }
            }
        }

        is ClassFilterConfig -> NestedConfigField(
            label = label,
            description = description,
            value = currentValue,
            onChange = onChange
        )
        is Boolean -> InspectorCard(label = label, description = description) {
            BooleanField(
                value = currentValue,
                onChange = onChange
            )
        }
        is String -> InspectorCard(label = label, description = description) {
            StringField(
                value = currentValue,
                onChange = onChange
            )
        }
        is Enum<*> -> InspectorCard(label = label, description = description) {
            EnumField(
                value = currentValue,
                onChange = onChange
            )
        }
        is List<*> -> InspectorCard(label = label, description = description) {
            ListField(
                value = currentValue,
                onChange = onChange
            )
        }
        null -> InspectorCard(label = label, description = description) {
            ReadOnlyValue(
                "null",
                "Nullable fields are not editable in this prototype."
            )
        }
        else -> InspectorCard(label = label, description = description) {
            ReadOnlyValue(
                currentValue::class.simpleName ?: "Value",
                currentValue.toString()
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
    CardExpanderItem(
        icon = null,
        heading = {
            Column(
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text(
                    label,
                    style = FluentTheme.typography.bodyStrong,
                )
                if (description != null) {
                    Text(
                        description,
                        color = FluentTheme.colors.text.text.secondary,
                        style = FluentTheme.typography.caption
                    )
                }
            }
        },
        dropdown = content
    )
}

@Composable
private fun BooleanField(value: Boolean, onChange: (Any?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (value) "On" else "Off", color = FluentTheme.colors.text.text.secondary)
        Switcher(checked = value, onCheckStateChange = { it: Boolean -> onChange(it) }, text = null)
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
private fun DecimalField(value: Decimal, onChange: (Any?) -> Unit) {
    UiTextField(
        value = "%.4f".format(value).trimEnd('0').trimEnd('.'),
        onValueChange = { text -> text.toBigDecimalOrNull()?.let { onChange(it) } },
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
    onValueChange: (Any?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var typedValue by remember(value) { mutableStateOf(value.toString()) }
    var fieldFocus by remember { mutableStateOf(false) }

    Expander(
        expanded = expanded,
        onExpandedChanged = { expanded = it },
        heading = {
            Column(
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text(
                    label,
                    style = FluentTheme.typography.bodyStrong,
                )
                if (description != null) {
                    Text(
                        description,
                        color = FluentTheme.colors.text.text.secondary,
                        style = FluentTheme.typography.caption
                    )
                }
            }
        },
        icon = null,
        trailing = {
            TextField(
                value = typedValue,
                onValueChange = { str ->
                    typedValue = str
                    str.toIntOrNull()?.let {
                        onValueChange(it)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions.Default,
                modifier = Modifier.onFocusChanged { state ->
                    fieldFocus = state.isFocused
                    if (!state.isFocused) {
                        typedValue = value.toString()
                    }
                }
            )
        }
    ) {
        val sliderStep = range.step
        val sliderMin = range.min
        val sliderMax = range.max
        val steps = max((sliderMax - sliderMin - 1) / sliderStep, 1)
        val sliderState = remember(fieldFocus) {
            SliderState(
                value.toFloat(),
                steps,
                true,
                { },
                sliderMin.toFloat()..sliderMax.toFloat()
            )
        }
        sliderState.value = value.toFloat()
        sliderStateOnValueChangeProp.set(sliderState) {
            typedValue = sliderState.nearestValue().toInt().toString()
        }
        CardExpanderItem(heading = {}) {
            Slider(
                state = sliderState,
                showTickMark = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Suppress("UNCHECKED_CAST")
private val sliderStateOnValueChangeProp = SliderState::class.memberProperties
    .find { it.name == "onValueChange" }!!
    .run {
        isAccessible = true
        this as KMutableProperty1<SliderState, (Float) -> Unit>
    }

@Composable
private fun DecimalSliderField(
    label: String,
    description: String?,
    value: Decimal,
    range: DecimalRangeVal,
    onValueChange: (Any?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var typedValue by remember(value) { mutableStateOf(value.toString()) }
    var fieldFocus by remember { mutableStateOf(false) }

    Expander(
        expanded = expanded,
        onExpandedChanged = { expanded = it },
        heading = {
            Column(
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text(
                    label,
                    style = FluentTheme.typography.bodyStrong,
                )
                if (description != null) {
                    Text(
                        description,
                        color = FluentTheme.colors.text.text.secondary,
                        style = FluentTheme.typography.caption
                    )
                }
            }
        },
        icon = null,
        trailing = {
            TextField(
                value = typedValue,
                onValueChange = { str ->
                    typedValue = str
                    str.toBigDecimalOrNull()?.let {
                        onValueChange(it)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions.Default,
                modifier = Modifier.onFocusChanged { state ->
                    fieldFocus = state.isFocused
                    if (!state.isFocused) {
                        typedValue = value.toString()
                    }
                }
            )
        },
    ) {
        val sliderStep = range.step.toBigDecimal()
        val sliderMin = range.min.toBigDecimal()
        val sliderMax = range.max.toBigDecimal()
        val steps = max(((sliderMax - sliderMin) / sliderStep).toInt() - 1, 1)
        val sliderState = remember(fieldFocus) {
            SliderState(
                value.toFloat(),
                steps,
                true,
                {
                    onValueChange((it.toBigDecimal() / sliderStep).setScale(0, RoundingMode.HALF_UP) * sliderStep)
                },
                sliderMin.toFloat()..sliderMax.toFloat()
            )
        }
        sliderState.value = value.toFloat()
        sliderStateOnValueChangeProp.set(sliderState) {
            val newValue =
                (sliderState.nearestValue().toBigDecimal() / sliderStep).setScale(0, RoundingMode.HALF_UP) * sliderStep
            typedValue = newValue.toString()
        }
        CardExpanderItem(heading = {}, icon = null) {
            Slider(
                state = sliderState,
                showTickMark = false,
                modifier = Modifier.fillMaxWidth()
                    .padding(end = 8.dp),
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
