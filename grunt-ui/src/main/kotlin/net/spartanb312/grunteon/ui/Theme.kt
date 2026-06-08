package net.spartanb312.grunteon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    background = Color(0xFF111318),
    panel = Color(0xFF191C22),
    panelAlt = Color(0xFF20242B),
    selectedPanel = Color(0xFF233043),
    nestedPanel = Color(0xFF141820),
    stroke = Color(0xFF303640),
    text = Color(0xFFE7ECF3),
    muted = Color(0xFF9AA3AF),
    accent = Color(0xFF75B8FF),
    warning = Color(0xFFFFC857),
)

val LightPalette = UiPalette(
    background = Color(0xFFF4F6FA),
    panel = Color(0xFFFFFFFF),
    panelAlt = Color(0xFFF0F3F8),
    selectedPanel = Color(0xFFE6F1FF),
    nestedPanel = Color(0xFFF8FAFD),
    stroke = Color(0xFFD5DAE2),
    text = Color(0xFF1D2430),
    muted = Color(0xFF5F6875),
    accent = Color(0xFF1467B8),
    warning = Color(0xFF9F6B00),
)

val LocalUiPalette = staticCompositionLocalOf { DarkPalette }

const val BaseFontScale = 0.85f
const val MinFontScale = 0.8f
const val DefaultFontScale = 1.0f
const val MaxFontScale = 1.3f
val UiCornerRadius: Dp = 6.dp
val UiPanelShape = RoundedCornerShape(UiCornerRadius)
val UiControlShape = RoundedCornerShape(UiCornerRadius)
val UiShapes = Shapes(
    extraSmall = UiControlShape,
    small = UiControlShape,
    medium = UiPanelShape,
    large = UiPanelShape,
    extraLarge = UiPanelShape,
)

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
    Surface(
        color = color,
        shape = UiPanelShape,
        border = BorderStroke(1.dp, palette.stroke),
        modifier = modifier
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
    Button(onClick = onClick, modifier = modifier, enabled = enabled, shape = UiControlShape) {
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
    OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, shape = UiControlShape) {
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
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled, shape = UiControlShape) {
        content()
    }
}
