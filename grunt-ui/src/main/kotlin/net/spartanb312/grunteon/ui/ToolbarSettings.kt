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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.*
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

private val ToolbarTabWidth = 128.dp

private fun openExternalLink(url: String): Boolean {
    return runCatching {
        check(Desktop.isDesktopSupported()) {
            "Desktop browsing is not supported"
        }
        val desktop = Desktop.getDesktop()
        check(desktop.isSupported(Desktop.Action.BROWSE)) {
            "Desktop browsing is not supported"
        }
        desktop.browse(URI(url))
    }.isSuccess
}

@Suppress("DeferredResultUnused")
@Composable
fun TopToolbar(
    appModel: AppModel,
    isMaximized: Boolean,
    showWindowControls: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
) {
    fun requestNewConfig() {
        appModel.checkUnsavedChanges {
            appModel.newConfig()
        }
    }

    fun requestOpenConfig() {
        return appModel.checkUnsavedChanges {
            appModel.coroutineScope.launch {
                chooseConfigPath()?.let(appModel::openConfig)
            }
        }
    }

    fun requestSaveConfig(): Deferred<Boolean> {
        return appModel.coroutineScope.async {
            (appModel.appState.configPath ?: chooseSaveConfigPath())?.let(appModel::saveConfig) == true
        }
    }

    fun requestSaveConfigAs(): Deferred<Boolean> {
        return appModel.coroutineScope.async {
            chooseSaveConfigPath(appModel.appState.configPath)?.let(appModel::saveConfig) == true
        }
    }

    appModel.discardConfirmState?.let { state ->
        ContentDialog(
            title = "Confirm Discard Changes",
            visible = true,
            primaryButtonText = "Save",
            secondaryButtonText = "Discard",
            closeButtonText = "Cancel",
            onButtonClick = {
                when (it) {
                    ContentDialogButton.Primary -> {
                        appModel.coroutineScope.launch {
                            if (requestSaveConfig().await()) {
                                state.onSave()
                            } else {
                                state.onCancel()
                            }
                        }
                    }
                    ContentDialogButton.Secondary -> {
                        state.onDiscard()
                    }
                    ContentDialogButton.Close -> {
                        state.onCancel()
                    }
                }
                appModel.discardConfirmState = null
            },
            content = {
                Text("You have unsaved changes. Do you want to save them before proceeding?")
            }
        )
    }

    Column(
        modifier = Modifier
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown || !it.isCtrlPressed) return@onPreviewKeyEvent false
                when (it.key) {
                    Key.N -> {
                        requestNewConfig()
                        true
                    }

                    Key.O -> {
                        requestOpenConfig()
                        true
                    }

                    Key.S -> {
                        if (it.isShiftPressed) requestSaveConfigAs() else requestSaveConfig()
                        true
                    }
                    else -> false
                }
            }
    ) {
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
                                requestNewConfig()
                                isFlyoutVisible = false
                            },
                            icon = Icons.Default.Document,
                            text = "New Config",
                            trailingText = "Ctrl+N",
                        )
                        MenuFlyoutButton(
                            onClick = {
                                isFlyoutVisible = false
                                requestOpenConfig()
                            },
                            icon = Icons.Default.FolderOpen,
                            text = "Open Config",
                            trailingText = "Ctrl+O",
                        )
                        MenuFlyoutSeparator()
                        MenuFlyoutButton(
                            onClick = {
                                isFlyoutVisible = false
                                requestSaveConfig()
                            },
                            icon = Icons.Default.Save,
                            text = "Save Config",
                            trailingText = "Ctrl+S",
                        )
                        MenuFlyoutButton(
                            onClick = {
                                isFlyoutVisible = false
                                requestSaveConfigAs()
                            },
                            icon = Icons.Default.SaveEdit,
                            text = "Save Config As",
                            trailingText = "Ctrl+Shift+S",
                        )
                        MenuFlyoutSeparator()
                        MenuFlyoutButton(
                            onClick = {
                                isFlyoutVisible = false
                                appModel.onExit()
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
                                openExternalLink("https://github.com/SpartanB312/Grunt/wiki")
                                isFlyoutVisible = false
                            },
                            icon = Icons.Default.BookQuestionMark,
                            text = "Help",
                        )
                        MenuFlyoutSeparator()
                        MenuFlyoutButton(
                            onClick = {
                                openExternalLink("https://github.com/SpartanB312/Grunt/issues")
                                isFlyoutVisible = false
                            },
                            icon = Icons.Default.Bug,
                            text = "Submit a Bug Report",
                        )
                        MenuFlyoutButton(
                            onClick = {
                                openExternalLink("https://github.com/SpartanB312/Grunt/issues")
                                isFlyoutVisible = false
                            },
                            icon = Icons.Default.ChatHelp,
                            text = "Submit Feature Request",
                        )
                        MenuFlyoutSeparator()
                        MenuFlyoutButton(
                            onClick = {
                                openExternalLink("https://github.com/SpartanB312/Grunt/releases")
                                isFlyoutVisible = false
                            },
                            icon = Icons.Default.Globe,
                            text = "Check for Updates",
                        )
                        MenuFlyoutButton(
                            onClick = {
                                // TODO: built-in about me page
                                openExternalLink("https://github.com/SpartanB312/Grunt")
                                isFlyoutVisible = false
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
                        onClick = appModel::onExit,
                    )
                }
            }
        }
        TabRow(
            { appModel.uiState.currentPage.ordinal },
            borderColor = Color.Transparent,
        ) {
            AppPage.entries.forEach { page ->
                item {
                    val selected = appModel.uiState.currentPage == page
                    TabItem(
                        selected = selected,
                        onSelectedChanged = { if (it) appModel.uiState.currentPage = page },
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
