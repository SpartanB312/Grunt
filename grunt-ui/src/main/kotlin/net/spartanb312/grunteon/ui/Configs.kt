package net.spartanb312.grunteon.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch
import kotlinx.serialization.Transient
import net.spartanb312.grunteon.obfuscator.lang.I18n
import net.spartanb312.grunteon.obfuscator.lang.I18nDescriptorRegistry
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Decimal
import net.spartanb312.grunteon.obfuscator.util.interfaces.DisplayEnum
import java.math.RoundingMode
import kotlin.math.max
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

private data class PathBrowseSpec(
    val browseLabel: String,
    val browse: suspend (currentValue: String) -> String?,
    val browseDirectoryLabel: String? = null,
    val browseDirectory: (suspend (currentValue: String) -> String?)? = null,
)

@Suppress("UNCHECKED_CAST")
@Composable
fun <T : Any> ConfigEditor(
    value: T,
    onChange: (T) -> Unit,
    descriptorBasePath: String? = null,
) = ConfigEditor(
    clazz = value::class as KClass<T>,
    value = value,
    onChange = onChange,
    descriptorBasePath = descriptorBasePath,
)

@Suppress("UNCHECKED_CAST")
@Composable
fun <T : Any> ConfigEditor(
    clazz: KClass<T>,
    value: T,
    onChange: (T) -> Unit,
    descriptorBasePath: String? = null,
) {
    val copyFunc = clazz.memberFunctions.find { member -> member.name == "copy" }
    checkNotNull(copyFunc) { "$clazz is not a data class" }
    val copyFunParameterOrder = copyFunc.parameters.drop(1).withIndex().associate { it.value.name!! to it.index }
    val properties = clazz.memberProperties
        .filter { it.javaField != null }
        .filter { it.annotations.none { ann -> ann is HiddenFromAutoParameter || ann is Transient } }
        .sortedBy { copyFunParameterOrder[it.name] ?: Int.MAX_VALUE }

    properties.forEachIndexed { index, property ->
        val propValue = property.get(value)
        val newParameterFunc = { newValue: Any? ->
            val newParameters = copyFunc.callBy(
                mapOf(
                    copyFunc.parameters[0] to value,
                    copyFunc.parameters[1 + copyFunParameterOrder[property.name]!!] to newValue
                )
            ) as T
            onChange(newParameters)
        }
        ConfigField(
            index,
            property,
            propValue,
            newParameterFunc,
            descriptorBasePath
        )
    }
}

private data class LocalizedFieldInfo(
    val fieldPath: String?,
    val label: String,
    val description: String?,
    val section: String?,
)

private fun KProperty<*>.localizedInfo(valueClass: KClass<*>?, descriptorBasePath: String?): LocalizedFieldInfo {
    val fieldPath = descriptorBasePath?.let { I18nDescriptorRegistry.configFieldPath(it, name) }
    val labelFallback = findAnnotation<SettingName>()?.enText
        ?: valueClass?.findAnnotation<SettingName>()?.enText
        ?: camelCaseToWords(name)
    val descriptionFallback = findAnnotation<SettingDesc>()?.enText
        ?: valueClass?.findAnnotation<SettingDesc>()?.enText
    val sectionFallback = findAnnotation<SettingSection>()?.enText
        ?: valueClass?.findAnnotation<SettingSection>()?.enText
    return LocalizedFieldInfo(
        fieldPath = fieldPath,
        label = localize(fieldPath?.let { "$it.name" }, labelFallback),
        description = descriptionFallback?.let { localize(fieldPath?.let { path -> "$path.desc" }, it) },
        section = sectionFallback?.let { localize(fieldPath?.let { path -> "$path.section" }, it) },
    )
}

private fun localize(key: String?, fallback: String): String =
    key?.let { I18n.text(it, fallback) } ?: fallback

@Suppress("UNCHECKED_CAST")
@Composable
private fun ConfigField(
    index: Int,
    prop: KProperty<*>,
    propValue: Any?,
    onChange: (Any?) -> Unit,
    descriptorBasePath: String?,
) {
    val propValueClass = propValue?.let { it::class }
    val fieldInfo = prop.localizedInfo(propValueClass, descriptorBasePath)
    val label = fieldInfo.label
    val description = fieldInfo.description

    if (fieldInfo.section != null) {
        Text(
            fieldInfo.section,
            style = FluentTheme.typography.bodyStrong,
            modifier = Modifier.padding(start = 2.dp, top = if (index == 0) 2.dp else 32.dp, bottom = 8.dp)
        )
    }

    val pathBrowseSpec = prop.pathBrowseSpec()
    when (propValue) {
        is String -> InspectorCard(label = label, description = description) {
            if (prop.isNullableString()) {
                NullableStringField(
                    value = propValue,
                    onValueChange = onChange,
                    browseSpec = pathBrowseSpec
                )
            } else if (pathBrowseSpec != null) {
                PathField(
                    value = propValue,
                    onValueChange = onChange,
                    browseSpec = pathBrowseSpec
                )
            } else {
                StringField(
                    value = propValue,
                    onValueChange = onChange
                )
            }
        }
        null -> InspectorCard(label = label, description = description) {
            if (prop.isNullableString()) {
                NullableStringField(
                    value = null,
                    onValueChange = onChange,
                    browseSpec = pathBrowseSpec
                )
            } else {
                ReadOnlyValue("null")
            }
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
                onValueChange = onChange
            )
        }
        is List<*> ->
            ListField(
                prop,
                label = label,
                description = description,
                value = propValue as List<Any>,
                onValueChange = onChange,
                descriptorFieldPath = fieldInfo.fieldPath,
            )
        else -> {
            val propType = propValue::class
            when {
                propType.isData || propValue::class.isData -> {
                    NestedConfigField(
                        label = label,
                        description = description,
                        value = propValue,
                        onChange = onChange,
                        descriptorBasePath = propType.descriptorConfigBase(fieldInfo.fieldPath),
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

private fun KProperty<*>.isNullableString(): Boolean =
    returnType.isMarkedNullable && returnType.classifier == String::class

@Composable
private fun BooleanField(value: Boolean, onChange: (Any?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (value) uiText(UiText.ConfigEditor.On) else uiText(UiText.ConfigEditor.Off),
            color = FluentTheme.colors.text.text.secondary
        )
        Switcher(checked = value, onCheckStateChange = { it: Boolean -> onChange(it) }, text = null)
    }
}

@Composable
private fun StringField(value: String, onValueChange: (Any?) -> Unit) {
    TextField(
        value = value,
        onValueChange = { onValueChange(it) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun NullableStringField(
    value: String?,
    onValueChange: (Any?) -> Unit,
    browseSpec: PathBrowseSpec?,
) {
    val coroutineScope = rememberCoroutineScope()
    val textValue = value.orEmpty()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextField(
            value = textValue,
            onValueChange = { onValueChange(it) },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        if (browseSpec != null) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        browseSpec.browse(textValue)?.let { onValueChange(it) }
                    }
                },
                modifier = Modifier.width(if (browseSpec.browseDirectory == null) 96.dp else 72.dp)
            ) {
                Text(browseSpec.browseLabel)
            }
            if (browseSpec.browseDirectory != null && browseSpec.browseDirectoryLabel != null) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            browseSpec.browseDirectory.invoke(textValue)?.let { onValueChange(it) }
                        }
                    },
                    modifier = Modifier.width(72.dp)
                ) {
                    Text(browseSpec.browseDirectoryLabel)
                }
            }
        }
        Button(
            onClick = { onValueChange(null) },
            iconOnly = true
        ) {
            Icon(
                imageVector = Icons.Default.Dismiss,
                contentDescription = uiText(UiText.ConfigEditor.ClearValue)
            )
        }
    }
}

@Composable
private fun IntField(value: Int, onValueChange: (Any?) -> Unit) {
    TextField(
        value = value.toString(),
        onValueChange = { it.toIntOrNull()?.let { onValueChange(it) } },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun DecimalField(value: Decimal, onValueChange: (Any?) -> Unit) {
    TextField(
        value = value.toString(),
        onValueChange = { it.toBigDecimalOrNull()?.let { onValueChange(it) } },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun EnumField(value: Enum<*>, onValueChange: (Any?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    fun enumName(enumConst: Enum<*>): String =
        (enumConst as? DisplayEnum)?.displayString ?: enumConst.name
    DropDownButton(
        onClick = { expanded = true },
    ) {
        Text(enumName(value))
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        val enumClass = value::class.java
        enumClass.enumConstants.forEach { enumConst ->
            DropdownMenuItem(
                onClick = {
                    onValueChange(enumConst)
                    expanded = false
                },
            ) {
                Text(enumName(enumConst))
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
        val sliderStepDecimal = range.step.toBigDecimal()
        val sliderMin = range.min
        val sliderMax = range.max
        fun Float.snappedValue(): Decimal {
            return (this.toBigDecimal() / sliderStepDecimal).setScale(0, RoundingMode.HALF_UP) * sliderStepDecimal
        }

        val sliderState = remember(fieldFocus) {
            SliderState(
                value.toFloat(),
                max((sliderMax - sliderMin - 1) / sliderStep, 1),
                true,
                {
                    if (!fieldFocus) {
                        onValueChange(it.snappedValue().toInt())
                    }
                },
                sliderMin.toFloat()..sliderMax.toFloat()
            )
        }
        sliderStateOnValueChangeProp.set(sliderState) {
            typedValue = sliderState.nearestValue().snappedValue().toString()
        }
        CardExpanderItem(heading = {}, icon = null) {
            Slider(
                state = sliderState,
                showTickMark = false,
                modifier = Modifier.fillMaxWidth(),
                tooltipContent = {
                    Text(sliderState.nearestValue().snappedValue().toString())
                }
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
        fun Float.snappedValue(): Decimal {
            return (this.toBigDecimal() / sliderStep).setScale(0, RoundingMode.HALF_UP) * sliderStep
        }

        val sliderState = remember(fieldFocus, value) {
            SliderState(
                value.toFloat(),
                max(((sliderMax - sliderMin) / sliderStep).toInt() - 1, 1),
                true,
                {
                    if (!fieldFocus) {
                        onValueChange(it.snappedValue())
                    }
                },
                sliderMin.toFloat()..sliderMax.toFloat()
            )
        }
        sliderStateOnValueChangeProp.set(sliderState) {
            typedValue = sliderState.nearestValue().snappedValue().toString()
        }
        CardExpanderItem(heading = {}, icon = null) {
            Slider(
                state = sliderState,
                showTickMark = false,
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                tooltipContent = {
                    Text(sliderState.nearestValue().snappedValue().toString())
                }
            )
        }
    }
}

@Composable
private fun NestedConfigField(
    label: String,
    description: String?,
    value: Any,
    onChange: (Any?) -> Unit,
    descriptorBasePath: String?,
) {
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
                ConfigEditor(
                    value = value,
                    onChange = onChange,
                    descriptorBasePath = descriptorBasePath,
                )
            }
        },
        icon = null
    )
}

@Suppress("UNCHECKED_CAST")
private fun <E : Any> KProperty<*>.newListEntryValue(): E? {
    val elementClass = returnType.arguments.firstOrNull()?.type?.classifier as? KClass<*> ?: return null
    val value = when (elementClass) {
        String::class -> ""
        Int::class -> 0
        Decimal::class -> Decimal.ZERO
        Boolean::class -> false
        else -> elementClass.defaultDataClassValue()
    } ?: return null
    return value as? E
}

private fun KClass<*>.defaultDataClassValue(): Any? {
    if (!isData) return null
    val constructor = primaryConstructor ?: return null
    if (constructor.parameters.any { !it.isOptional }) return null
    return runCatching { constructor.callBy(emptyMap()) }.getOrNull()
}

private data class ConfigDisplayInfo(
    val label: String,
    val description: String?,
)

private fun KClass<*>.configDisplayInfo(fallback: String, descriptorBasePath: String?): ConfigDisplayInfo {
    val labelFallback = findAnnotation<SettingName>()?.enText
        ?: simpleName?.let(::camelCaseToWords)
        ?: fallback
    val descriptionFallback = findAnnotation<SettingDesc>()?.enText
    return ConfigDisplayInfo(
        label = localize(descriptorBasePath?.let { "$it.name" }, labelFallback),
        description = descriptionFallback?.let { localize(descriptorBasePath?.let { path -> "$path.desc" }, it) },
    )
}

private fun KClass<*>.descriptorConfigBase(defaultBasePath: String?): String? =
    I18nDescriptorRegistry.classOverrideConfigBase(this)?.let { overrideBasePath ->
        if (defaultBasePath?.startsWith("ui.") == true) uiDescriptorPath(overrideBasePath) else overrideBasePath
    } ?: defaultBasePath

@Suppress("UNCHECKED_CAST")
@Composable
private fun <E : Any> ListField(
    prop: KProperty<*>,
    label: String,
    description: String?,
    value: List<E>,
    onValueChange: (List<E>) -> Unit,
    descriptorFieldPath: String?,
) {
    val listUpdater = ListUpdater({ value }, onValueChange)
    val pathBrowseSpec = prop.pathBrowseSpec()

    @Composable
    fun ListEntryCard(
        index: Int,
        content: @Composable () -> Unit
    ) {
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
                            contentDescription = uiText(UiText.ConfigEditor.DuplicateEntry)
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
                            contentDescription = uiText(UiText.ConfigEditor.DeleteEntry)
                        )
                    }
                }
            }
        )
    }

    @Composable
    fun DataClassListEntryCard(
        index: Int,
        displayInfo: ConfigDisplayInfo,
        content: @Composable () -> Unit
    ) {
        var itemExpanded by remember { mutableStateOf(false) }
        Expander(
            expanded = itemExpanded,
            onExpandedChanged = { itemExpanded = it },
            icon = null,
            heading = {
                Column(
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Text(
                        displayInfo.label,
                        style = FluentTheme.typography.bodyStrong,
                    )
                    if (displayInfo.description != null) {
                        Text(
                            displayInfo.description,
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
                    Button(
                        onClick = { listUpdater.add(index, value[index]) },
                        iconOnly = true
                    ) {
                        Icon(
                            imageVector = Icons.Default.Copy,
                            contentDescription = uiText(UiText.ConfigEditor.DuplicateEntry)
                        )
                    }
                    Button(
                        onClick = { listUpdater.removeAt(index) },
                        iconOnly = true
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = uiText(UiText.ConfigEditor.DeleteEntry)
                        )
                    }
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                content()
            }
        }
    }

    var expanded by remember { mutableStateOf(false) }
    val newEntryValue = prop.newListEntryValue<E>()
    val newEntry: (() -> Unit)? = newEntryValue?.let { entry ->
        {
            expanded = true
            listUpdater.add(entry)
        }
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
                            contentDescription = uiText(UiText.ConfigEditor.AddEntry)
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
                        contentDescription = uiText(UiText.ConfigEditor.ClearList)
                    )
                }
            }
        }
    ) {
        if (listUpdater.isEmpty()) {
            Text(
                uiText(UiText.ConfigEditor.EmptyList),
                modifier = Modifier.padding(12.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = FluentTheme.typography.caption
            )
            return@Expander
        }
        listUpdater.forEachIndexed { index, item ->
            val onChange = { newItem: Any? ->
                if (newItem != null) {
                    listUpdater[index] = newItem as E
                }
            }
            when (item) {
                is String -> ListEntryCard(index) {
                    if (pathBrowseSpec != null) {
                        PathField(
                            value = item,
                            onValueChange = onChange,
                            browseSpec = pathBrowseSpec
                        )
                    } else {
                        StringField(
                            value = item,
                            onValueChange = onChange
                        )
                    }
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
                        onValueChange = onChange
                    )
                }
                else -> {
                    val propType = item::class
                    when {
                        propType.isData -> {
                            val itemDescriptorBase = descriptorFieldPath?.let { "$it.item" }
                            DataClassListEntryCard(
                                index = index,
                                displayInfo = propType.configDisplayInfo("#$index", itemDescriptorBase)
                            ) {
                                ConfigEditor(
                                    value = item,
                                    onChange = onChange,
                                    descriptorBasePath = propType.descriptorConfigBase(itemDescriptorBase),
                                )
                            }
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

private fun KProperty<*>.pathBrowseSpec(): PathBrowseSpec? = when (name) {
    "input" -> PathBrowseSpec(
        browseLabel = uiText(UiText.ConfigEditor.File),
        browse = { chooseInputPath(it)?.toString() },
        browseDirectoryLabel = uiText(UiText.ConfigEditor.Directory),
        browseDirectory = { chooseInputDirectory(it)?.toString() }
    )
    "output" -> PathBrowseSpec(
        browseLabel = uiText(UiText.ConfigEditor.Browse),
        browse = { chooseOutputPath(it)?.toString() }
    )
    "libs" -> PathBrowseSpec(
        browseLabel = uiText(UiText.ConfigEditor.File),
        browse = { chooseInputPath(it)?.toString() },
        browseDirectoryLabel = uiText(UiText.ConfigEditor.Directory),
        browseDirectory = { chooseInputDirectory(it)?.toString() }
    )
    else -> null
}

@Composable
private fun ReadOnlyValue(text: String) {
    val interactionSource1 = remember<MutableInteractionSource> { MutableInteractionSource() }
    val color = TextFieldDefaults.defaultTextFieldColors()
        .schemeFor(interactionSource1.collectVisualState(false, focusFirst = true))
    BasicTextField(
        modifier = Modifier.Companion,
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

@Composable
private fun PathField(
    value: String,
    onValueChange: (Any?) -> Unit,
    browseSpec: PathBrowseSpec,
) {
    val coroutineScope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextField(
            value = value,
            onValueChange = { onValueChange(it) },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Button(
            onClick = {
                coroutineScope.launch {
                    browseSpec.browse(value)?.let { onValueChange(it) }
                }
            },
            modifier = Modifier.width(if (browseSpec.browseDirectory == null) 96.dp else 72.dp)
        ) {
            Text(browseSpec.browseLabel)
        }
        if (browseSpec.browseDirectory != null && browseSpec.browseDirectoryLabel != null) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        browseSpec.browseDirectory.invoke(value)?.let { onValueChange(it) }
                    }
                },
                modifier = Modifier.width(72.dp)
            ) {
                Text(browseSpec.browseDirectoryLabel)
            }
        }
    }
}
