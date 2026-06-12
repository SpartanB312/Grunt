package net.spartanb312.grunteon.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.LocalTextStyle
import io.github.composefluent.component.*
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.Add
import io.github.composefluent.icons.regular.Copy
import io.github.composefluent.icons.regular.Delete
import io.github.composefluent.icons.regular.Dismiss
import io.github.composefluent.scheme.collectVisualState
import kotlinx.serialization.Transient
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Decimal
import java.math.RoundingMode
import kotlin.math.max
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

@Composable
fun Inspector(
    state: PipelineEditorState,
    modifier: Modifier = Modifier,
) {
    val selected = state.selectedIndex
    val entry = state.transformerList.getOrNull(selected)
    val definition = entry?.let { findDefinition(entry.config, state.definitions) }
    val transformerName = definition?.label ?: entry?.config?.let { it::class.simpleName }
    val transformerDesc = definition?.description ?: entry?.config?.let { it::class.qualifiedName }
    PanelSurface(
        if (transformerName == null) "Inspector" else "Inspector - $transformerName",
        transformerDesc ?: "Select a transformer node to edit its Config.",
        modifier
    ) {
        if (entry == null) return@PanelSurface
        val scrollState = rememberScrollState()
        ScrollbarContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp, 8.dp, 0.dp, 8.dp),
            adapter = rememberScrollbarAdapter(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(FluentTheme.shapes.control)
                    .padding(0.dp, 0.dp, 12.dp, 0.dp)
                    .verticalScroll(scrollState),
            ) {
                ConfigEditor(
                    value = entry.config,
                    onChange = {
                        state.transformerList[selected] = state.transformerList[selected].copy(config = it)
                    },
                )
            }
        }
    }
}


@Suppress("UNCHECKED_CAST")
@Composable
fun <T : Any> ConfigEditor(
    value: T,
    onChange: (T) -> Unit
) = ConfigEditor(
        clazz = value::class as KClass<T>,
        value = value,
        onChange = onChange
    )

@Suppress("UNCHECKED_CAST")
@Composable
fun <T : Any> ConfigEditor(
    clazz: KClass<T>,
    value: T,
    onChange: (T) -> Unit
) {
    val copyFunc = clazz.memberFunctions.find { member -> member.name == "copy" }
    checkNotNull(copyFunc) { "$clazz is not a data class" }
    val copyFunParameterOrder = copyFunc.parameters.drop(1).withIndex().associate { it.value.name!! to it.index }
    val properties = clazz.memberProperties
        .filter { it.javaField != null }
        .filter { it.annotations.none { ann -> ann is HiddenFromAutoParameter || ann is Transient } }
        .sortedBy { copyFunParameterOrder[it.name] ?: Int.MAX_VALUE }

    properties.forEach {
        val propValue = it.get(value)!!
        val newParameterFunc = { newValue: Any ->
            val newParameters = copyFunc.callBy(
                mapOf(
                    copyFunc.parameters[0] to value,
                    copyFunc.parameters[1 + copyFunParameterOrder[it.name]!!] to newValue
                )
            ) as T
            onChange(newParameters)
        }
        ConfigField(
            prop = it,
            propValue = propValue,
            onChange = newParameterFunc
        )
    }
}

@Suppress("UNCHECKED_CAST")
@Composable
private fun ConfigField(
    prop: KProperty<*>,
    propValue: Any,
    onChange: (Any) -> Unit
) {
    val label = prop.findAnnotation<SettingName>()?.enText
        ?: propValue::class.findAnnotation<SettingName>()?.enText
        ?: camelCaseToWords(prop.name)
    val description = prop.findAnnotation<SettingDesc>()?.enText
        ?: propValue::class.findAnnotation<SettingDesc>()?.enText

    when (propValue) {
        is String -> InspectorCard(label = label, description = description) {
            StringField(
                value = propValue,
                onValueChange = onChange
            )
        }
        is Int -> {
            val range = prop.findAnnotation<IntRangeVal>()
            if (range != null) {
                IntSliderField(
                    label = label,
                    description = description,
                    value = propValue,
                    range = range,
                    onValueChange = onChange
                )
            } else {
                InspectorCard(label = label, description = description) {
                    IntField(
                        value = propValue,
                        onValueChange = onChange
                    )
                }
            }
        }
        is Decimal -> {
            val range = prop.findAnnotation<DecimalRangeVal>()
            if (range != null) {
                DecimalSliderField(
                    label = label,
                    description = description,
                    value = propValue,
                    range = range,
                    onValueChange = onChange
                )
            } else {
                InspectorCard(label = label, description = description) {
                    DecimalField(
                        value = propValue,
                        onValueChange = onChange
                    )
                }
            }
        }
        is Boolean -> InspectorCard(label = label, description = description) {
            BooleanField(
                value = propValue,
                onChange = onChange
            )
        }
        is Enum<*> -> InspectorCard(label = label, description = description) {
            EnumField(
                value = propValue,
                onChange = onChange
            )
        }
        is List<*> ->
            ListField(
                prop,
                label = label,
                description = description,
                value = propValue as List<Any>,
                onValueChange = onChange
            )
        else -> {
            val propType = propValue::class
            when {
                propType.isData || propValue::class.isData -> {
                    NestedConfigField(
                        label = label,
                        description = description,
                        value = propValue,
                        onChange = onChange
                    )
                }
                else -> InspectorCard(label, description) {
                    ReadOnlyValue(propValue.toString())
                }
            }
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
                modifier = Modifier.fillMaxWidth(0.7f)
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
        dropdown = {
            Box(
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                content()
            }
        }
    )
}

@Composable
private fun BooleanField(value: Boolean, onChange: (Any) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (value) "On" else "Off", color = FluentTheme.colors.text.text.secondary)
        Switcher(checked = value, onCheckStateChange = { it: Boolean -> onChange(it) }, text = null)
    }
}

@Composable
private fun StringField(value: String, onValueChange: (Any) -> Unit) {
    TextField(
        value = value,
        onValueChange = { onValueChange(it) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun IntField(value: Int, onValueChange: (Any) -> Unit) {
    TextField(
        value = value.toString(),
        onValueChange = { it.toIntOrNull()?.let { onValueChange(it) } },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun DecimalField(value: Decimal, onValueChange: (Any) -> Unit) {
    TextField(
        value = value.toString(),
        onValueChange = { it.toBigDecimalOrNull()?.let { onValueChange(it) } },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun EnumField(value: Enum<*>, onChange: (Any) -> Unit) {
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
    onValueChange: (Any) -> Unit,
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
    onValueChange: (Any) -> Unit,
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
private fun NestedConfigField(label: String, description: String?, value: Any, onChange: (Any) -> Unit) {
    CardExpanderItem(
        heading = {
            var expanded by remember { mutableStateOf(false) }
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
                modifier = Modifier
                    .padding(end = 16.dp)
            ) {
                ConfigEditor(value = value, onChange = onChange)
            }
        },
        icon = null
    )
}

@Suppress("UNCHECKED_CAST")
@Composable
private fun <E : Any> ListField(
    prop: KProperty<*>,
    label: String,
    description: String?,
    value: List<E>,
    onValueChange: (List<E>) -> Unit
) {
    val listUpdater = ListUpdater({ value }, onValueChange)

    @Composable
    fun ListEntryCard(index: Int, content: @Composable () -> Unit) {
        CardExpanderItem(
            icon = null,
            heading = {
                Text("#${index}", color = FluentTheme.colors.text.text.secondary)
                Spacer(modifier = Modifier.width(24.dp))
            },
            dropdown = {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.weight(1.0f)
                    ) {
                        content()
                    }
                    Button(
                        onClick = {
                            listUpdater.add(index, value[index])
                        },
                        iconOnly = true
                    ) {
                        Icon(
                            imageVector = Icons.Default.Copy,
                            contentDescription = "Duplicate Entry"
                        )
                    }
                    Button(
                        onClick = {
                            listUpdater.removeAt(index)
                        },
                        iconOnly = true
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Entry"
                        )
                    }
                }
            }
        )
    }

    var expanded by remember { mutableStateOf(false) }
    val newEntry: (() -> Unit)? = when (prop.returnType.arguments.first().type?.classifier) {
        String::class -> {
            { listUpdater.add("" as E) }
        }
        Int::class -> {
            { listUpdater.add(0 as E) }
        }
        Decimal::class -> {
            { listUpdater.add(Decimal.ZERO as E) }
        }
        Boolean::class -> {
            { listUpdater.add(false as E) }
        }
        else -> null
    }

    Expander(
        expanded = expanded,
        onExpandedChanged = { expanded = it },
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
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (newEntry != null) {
                    Button(
                        onClick = newEntry,
                        iconOnly = true
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add entry"
                        )
                    }
                }
                Button(
                    onClick = {
                        listUpdater.clear()
                    },
                    iconOnly = true
                ) {
                    Icon(
                        imageVector = Icons.Default.Dismiss,
                        contentDescription = "Clear list"
                    )
                }
            }
        }
    ) {
        if (listUpdater.isEmpty()) {
            Text(
                "Empty List",
                modifier = Modifier.padding(12.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = FluentTheme.typography.caption
            )
            return@Expander
        }
        listUpdater.forEachIndexed { index, item ->
            val onChange = { newItem: Any ->
                listUpdater[index] = newItem as E
            }
            when (item) {
                is String -> ListEntryCard(index) {
                    StringField(
                        value = item,
                        onValueChange = onChange
                    )
                }
                is Int -> {
                    val range = prop.findAnnotation<IntRangeVal>()
                    if (range != null) {
                        IntSliderField(
                            label = label,
                            description = description,
                            value = item,
                            range = range,
                            onValueChange = onChange
                        )
                    } else {
                        ListEntryCard(index) {
                            IntField(
                                value = item,
                                onValueChange = onChange
                            )
                        }
                    }
                }
                is Decimal -> {
                    val range = prop.findAnnotation<DecimalRangeVal>()
                    if (range != null) {
                        DecimalSliderField(
                            label = label,
                            description = description,
                            value = item,
                            range = range,
                            onValueChange = onChange
                        )
                    } else {
                        ListEntryCard(index) {
                            DecimalField(
                                value = item,
                                onValueChange = onChange
                            )
                        }
                    }
                }
                is Boolean -> ListEntryCard(index) {
                    BooleanField(
                        value = item,
                        onChange = onChange
                    )
                }
                is Enum<*> -> ListEntryCard(index) {
                    EnumField(
                        value = item,
                        onChange = onChange
                    )
                }
                else -> {
                    val propType = item::class
                    when {
                        propType.isData -> {
                            NestedConfigField(
                                label = label,
                                description = description,
                                value = item,
                                onChange = onChange
                            )
                        }
                        else -> InspectorCard(label, description) {
                            ReadOnlyValue(item.toString())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyValue(text: String) {
    val interactionSource1 = remember<MutableInteractionSource> { MutableInteractionSource() }
    val color = TextFieldDefaults.defaultTextFieldColors()
        .schemeFor(interactionSource1.collectVisualState(false, focusFirst = true))
    BasicTextField(
        modifier = Modifier,
        value = text,
        onValueChange = {},
        textStyle = LocalTextStyle.current.copy(color = color.contentColor, fontFamily = FontFamily.Monospace),
        enabled = true,
        readOnly = true,
        singleLine = false,
        visualTransformation = VisualTransformation.None,
        maxLines = Int.MAX_VALUE,
        keyboardActions = KeyboardActions(),
        cursorBrush = color.cursorBrush,
        keyboardOptions = KeyboardOptions.Default,
        interactionSource = interactionSource1,
        decorationBox = { innerTextField ->
            TextFieldDefaults.DecorationBox(
                color = color,
                interactionSource = interactionSource1,
                innerTextField = innerTextField,
                value = text,
                enabled = true,
                placeholder = null,
                leadingIcon = null,
                onClearClick = null,
                header = null,
                trailing = null,
                shape = FluentTheme.shapes.control
            )
        }
    )
}
