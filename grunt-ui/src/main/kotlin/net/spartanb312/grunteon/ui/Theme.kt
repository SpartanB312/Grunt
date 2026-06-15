// TODO: cleanuo
package net.spartanb312.grunteon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Layer
import io.github.composefluent.component.*

val UiCornerRadius: Dp = 8.dp
val UiPanelShape = RoundedCornerShape(UiCornerRadius)

enum class ThemeMode {
    Auto,
    Dark,
    Light,
}

@Composable
fun ScrollPanel(
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    ScrollbarContainer(
        modifier = Modifier
            .padding(12.dp, 8.dp, 0.dp, 8.dp),
        adapter = rememberScrollbarAdapter(scrollState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(FluentTheme.shapes.control)
                .padding(0.dp, 0.dp, 12.dp, 0.dp)
                .verticalScroll(scrollState),
            content = content
        )
    }
}

@Composable
fun PanelSurface(
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Layer(
        modifier = Modifier
            .background(color = FluentTheme.colors.background.layer.default)
            .then(modifier),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(2.dp)
        ) {
            Column(
                Modifier
                    .padding(10.dp)
            ) {
                Text(
                    title,
                    style = FluentTheme.typography.subtitle,
                )
                if (description != null) {
                    Text(
                        description,
                        color = FluentTheme.colors.text.text.secondary
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun PanelSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FramedSurface(color = FluentTheme.colors.background.card.default, modifier = modifier, content = content)
}

@Composable
fun SectionSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FramedSurface(color = FluentTheme.colors.background.card.secondary, modifier = modifier, content = content)
}

@Composable
fun FramedSurface(
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(FluentTheme.shapes.control)
            .background(color)
            .border(BorderStroke(1.dp, FluentTheme.colors.stroke.card.default), UiPanelShape)
    ) {
        content()
    }
}

@Composable
fun UiButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        disabled = !enabled,
        buttonColors = ButtonDefaults.accentButtonColors(),
    ) {
        content()
    }
}

@Composable
fun UiOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Button(onClick = onClick, modifier = modifier, disabled = !enabled) {
        content()
    }
}

@Composable
fun UiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = (minLines * 24).dp),
        singleLine = singleLine,
        maxLines = maxLines,
        header = label?.let {
            { Text(it, color = FluentTheme.colors.text.text.secondary, style = FluentTheme.typography.caption) }
        },
    )
}

@Composable
fun UiCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    CheckBox(checked = checked, onCheckStateChange = onCheckedChange)
}

@Composable
fun UiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    modifier: Modifier = Modifier,
) {
    Slider(
        state = SliderState(value, steps, true, onValueChange, valueRange),
        modifier = modifier,
        showTickMark = steps in 1..20,
    )
}
