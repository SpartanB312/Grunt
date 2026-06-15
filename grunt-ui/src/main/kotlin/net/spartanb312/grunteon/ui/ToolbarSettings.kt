package net.spartanb312.grunteon.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.onClick
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.*
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.*
import kotlinx.coroutines.launch

private val ToolbarTabWidth = 128.dp

@Composable
fun TopToolbar(
    uiState: UIState,
    onNewConfig: () -> Unit,
    onOpenConfig: () -> Unit,
    onSaveConfig: () -> Boolean,
    onSaveConfigAs: () -> Unit,
    isMaximized: Boolean,
    showWindowControls: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onExit: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .background(color = FluentTheme.colors.background.mica.base)
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GrunteonLogo(modifier = Modifier.fillMaxHeight().padding(start = 12.dp, top = 8.dp, bottom = 4.dp))
            MenuBar(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp)
            ) {
                MenuBarItem(
                    items = {
                        MenuFlyoutButton(
                            onClick = {
                                onNewConfig()
                                isFlyoutVisible = false
                            },
                            icon = Icons.Default.Document,
                            text = "New Config",
                            trailingText = "Ctrl+N",
                        )
                        MenuFlyoutButton(
                            onClick = {
                                onOpenConfig()
                                isFlyoutVisible = false
                            },
                            icon = Icons.Default.FolderOpen,
                            text = "Open Config",
                            trailingText = "Ctrl+O",
                        )
                        MenuFlyoutSeparator()
                        MenuFlyoutButton(
                            onClick = {
                                onSaveConfig()
                                isFlyoutVisible = false
                            },
                            icon = Icons.Default.Save,
                            text = "Save Config",
                            trailingText = "Ctrl+S",
                        )
                        MenuFlyoutButton(
                            onClick = {
                                onSaveConfigAs()
                                isFlyoutVisible = false
                            },
                            icon = Icons.Default.SaveEdit,
                            text = "Save Config As",
                            trailingText = "Ctrl+Shift+S",
                        )
                        MenuFlyoutSeparator()
                        MenuFlyoutButton(
                            onClick = {
                                isFlyoutVisible = false
                                onExit()
                            },
                            icon = Icons.Default.Dismiss,
                            text = "Exit",
                        )
                    }
                ) {
                    Text("File")
                }


                MenuBarItem(
                    items = {
                        MenuFlyoutButton(
                            onClick = {
                                // TODO: Run obfuscation
                            },
                            icon = Icons.Default.Play,
                            text = "Run Obfuscation",
                        )
                    }
                ) {
                    Text("Tool")
                }
                MenuBarItem(
                    items = {
                        MenuFlyoutButton(
                            onClick = {
                                // TODO: Open help page
                            },
                            icon = Icons.Default.BookQuestionMark,
                            text = "Help",
                        )
                        MenuFlyoutSeparator()
                        MenuFlyoutButton(
                            onClick = {
                                // TODO: Open GitHub issue page
                            },
                            icon = Icons.Default.Bug,
                            text = "Submit a Bug Report",
                        )
                        MenuFlyoutButton(
                            onClick = {
                                // TODO: Open GitHub issue page
                            },
                            icon = Icons.Default.ChatHelp,
                            text = "Submit Feature Request",
                        )
                        MenuFlyoutSeparator()
                        MenuFlyoutButton(
                            onClick = {
                                isFlyoutVisible = false
                                onExit()
                            },
                            icon = Icons.Default.Globe,
                            text = "Check for Updates",
                        )
                        MenuFlyoutButton(
                            onClick = {
                                isFlyoutVisible = false
                                onExit()
                            },
                            icon = Icons.Default.Info,
                            text = "About",
                        )
                    }
                ) {
                    Text("Help")
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight())
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.Top
            ) {
                if (showWindowControls) {
                    WindowControlButton(
                        icon = Icons.Default.Subtract,
                        contentDescription = "Minimize",
                        buttonColors = ButtonDefaults.subtleButtonColors(),
                        onClick = onMinimize,
                    )
                    WindowControlButton(
                        icon = if (isMaximized) Icons.Default.SquareMultiple else Icons.Default.Square,
                        contentDescription = if (isMaximized) "Restore" else "Maximize",
                        buttonColors = ButtonDefaults.subtleButtonColors(),
                        onClick = onToggleMaximize,
                    )
                    val defaultSubtle = ButtonDefaults.subtleButtonColors()
                    WindowControlButton(
                        icon = Icons.Default.Dismiss,
                        contentDescription = "Close",
                        buttonColors = ButtonDefaults.subtleButtonColors(
                            hovered = defaultSubtle.hovered.copy(fillColor = Color(0xFFC42B1C)),
                            pressed = defaultSubtle.pressed.copy(fillColor = Color(0xFFB3271C))
                        ),
                        onClick = onExit,
                    )
                }
            }
        }
        TabRow(
            { uiState.currentPage.ordinal },
            borderColor = Color.Transparent,
        ) {
            AppPage.entries.forEach { page ->
                item {
                    val selected = uiState.currentPage == page
                    TabItem(
                        selected = selected,
                        onSelectedChanged = { if (it) uiState.currentPage = page },
                        modifier = Modifier.height(64.dp)
                    ) {
                        Row(modifier = Modifier.height(100.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                page.name,
                                modifier = Modifier
                                    .width(ToolbarTabWidth)
                                    .padding(4.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GrunteonLogo(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }
    var targetRotation by remember { mutableFloatStateOf(0f) }

    Image(
        painter = painterResource("logo.svg"),
        contentDescription = "Grunteon",
        modifier = modifier
            .graphicsLayer { rotationZ = rotation.value }
            .onClick {
                targetRotation += 360.0f * 2.0f
                scope.launch {
                    rotation.animateTo(
                        targetRotation,
                        initialVelocity = rotation.velocity * 4.0f,
                        animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing)
                    )
                }
            },
    )
}

@Composable
private fun WindowControlButton(
    icon: ImageVector,
    contentDescription: String,
    buttonColors: ButtonColorScheme,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        iconOnly = true,
        modifier = Modifier.height(40.dp).aspectRatio(1.33f).padding(bottom = 8.dp, top = 0.dp),
        buttonColors = buttonColors,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

@Composable
private fun MenuFlyoutScope.MenuFlyoutButton(
    onClick: () -> Unit,
    icon: ImageVector? = null,
    text: String,
    trailingText: String? = null,
    enabled: Boolean = true,
) {
    MenuFlyoutItem(
        onClick = onClick,
        icon = icon?.let { { Icon(imageVector = it, contentDescription = null) } },
        text = { Text(text) },
        trailing = trailingText?.let {
            {
                Spacer(Modifier.width(16.dp))
                Text(it, style = FluentTheme.typography.caption)
            }
        },
        enabled = enabled,
    )
}
