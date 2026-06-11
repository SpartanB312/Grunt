package net.spartanb312.grunteon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.*

data class UiPalette(
    val background: Color,
    val panel: Color,
    val panelAlt: Color,
    val selectedPanel: Color,
    val nestedPanel: Color,
    val stroke: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val warning: Color,
)

val DarkPalette = UiPalette(
    background = Color(0xFF202020),
    panel = Color(0xFF2C2C2C),
    panelAlt = Color(0xFF323232),
    selectedPanel = Color(0xFF173B57),
    nestedPanel = Color(0xFF272727),
    stroke = Color(0xFF454545),
    text = Color(0xFFFFFFFF),
    muted = Color(0xFFC5C5C5),
    accent = Color(0xFF60CDFF),
    warning = Color(0xFFFCE100),
)

val LightPalette = UiPalette(
    background = Color(0xFFF3F3F3),
    panel = Color(0xFFFBFBFB),
    panelAlt = Color(0xFFFFFFFF),
    selectedPanel = Color(0xFFE9F5FC),
    nestedPanel = Color(0xFFF7F7F7),
    stroke = Color(0xFFE0E0E0),
    text = Color(0xFF1A1A1A),
    muted = Color(0xFF5D5D5D),
    accent = Color(0xFF005FB8),
    warning = Color(0xFF9D5D00),
)

val LocalUiPalette = staticCompositionLocalOf { DarkPalette }

const val BaseFontScale = 0.85f
const val MinFontScale = 0.8f
const val DefaultFontScale = 1.0f
const val MaxFontScale = 1.3f
val UiCornerRadius: Dp = 4.dp
val UiPanelShape = RoundedCornerShape(UiCornerRadius)
val UiControlShape = RoundedCornerShape(UiCornerRadius)

enum class ThemeMode {
    Dark,
    Light,
}

@Composable
fun PanelSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val palette = LocalUiPalette.current
    FramedSurface(color = palette.panel, modifier = modifier, content = content)
}

@Composable
fun SectionSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val palette = LocalUiPalette.current
    FramedSurface(color = palette.panelAlt, modifier = modifier, content = content)
}

@Composable
fun NestedSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val palette = LocalUiPalette.current
    FramedSurface(color = palette.nestedPanel, modifier = modifier, content = content)
}

@Composable
fun FramedSurface(
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = LocalUiPalette.current
    Box(
        modifier = modifier
            .clip(UiPanelShape)
            .background(color)
            .border(BorderStroke(1.dp, palette.stroke), UiPanelShape)
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
fun UiTextButton(
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
fun UiTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = FluentTheme.typography.title)
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
        modifier = modifier,
        singleLine = singleLine,
        maxLines = maxLines,
        header = label?.let {
            { Text(it, color = LocalUiPalette.current.muted, style = FluentTheme.typography.caption) }
        },
    )
}

@Composable
fun UiSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switcher(checked = checked, onCheckStateChange = onCheckedChange, text = null)
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
