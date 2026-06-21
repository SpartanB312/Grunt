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

    val errorDialog = appModel.uiState.errorDialog
    ContentDialog(
        title = errorDialog?.title ?: "",
        visible = errorDialog != null,
        primaryButtonText = uiText(UiText.Dialog.Ok),
        onButtonClick = {
            appModel.uiState.errorDialog = null
        },
        content = {
            Text(errorDialog?.message ?: "")
        }
    )

    val discardConfirmState = appModel.discardConfirmState
    ContentDialog(
        title = uiText(UiText.Dialog.ConfirmDiscardConfigChangesTitle),
        visible = discardConfirmState != null,
        primaryButtonText = uiText(UiText.Dialog.Save),
        secondaryButtonText = uiText(UiText.Dialog.Discard),
        closeButtonText = uiText(UiText.Dialog.Cancel),
        onButtonClick = {
            discardConfirmState?.let { state ->
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
            }
        },
        content = {
            Text(uiText(UiText.Dialog.ConfirmDiscardConfigChangesMessage))
        }
    )

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
                            text = uiText(UiText.Toolbar.NewConfig),
                            trailingText = "Ctrl+N",
                        )
                        MenuFlyoutButton(
                            onClick = {
                                isFlyoutVisible = false
                                requestOpenConfig()
                            },
                            icon = Icons.Default.FolderOpen,
                            text = uiText(UiText.Toolbar.OpenConfig),
                            trailingText = "Ctrl+O",
                        )
                        MenuFlyoutSeparator()
                        MenuFlyoutButton(
                            onClick = {
                                isFlyoutVisible = false
                                requestSaveConfig()
                            },
                            icon = Icons.Default.Save,
                            text = uiText(UiText.Toolbar.SaveConfig),
                            trailingText = "Ctrl+S",
                        )
                        MenuFlyoutButton(
                            onClick = {
                                isFlyoutVisible = false
                                requestSaveConfigAs()
                            },
                            icon = Icons.Default.SaveEdit,
                            text = uiText(UiText.Toolbar.SaveConfigAs),
                            trailingText = "Ctrl+Shift+S",
                        )
                        MenuFlyoutSeparator()
                        MenuFlyoutButton(
                            onClick = {
                                isFlyoutVisible = false
                                appModel.onExit()
                            },
                            icon = Icons.Default.Dismiss,
                            text = uiText(UiText.Toolbar.Exit),
                        )
                    }
                ) {
                    Text(uiText(UiText.Toolbar.File))
                }


                MenuBarItem(
                    items = {
                        MenuFlyoutButton(
                            onClick = {
                                // TODO: Run obfuscation
                            },
                            icon = Icons.Default.Play,
                            text = uiText(UiText.Toolbar.RunObfuscation),
                        )
                    }
                ) {
                    Text(uiText(UiText.Toolbar.Tool))
                }
                MenuBarItem(
                    items = {
                        MenuFlyoutButton(
                            onClick = {
                                openExternalLink("https://github.com/SpartanB312/Grunt/wiki")
                                isFlyoutVisible = false
                            },
                            icon = Icons.Default.BookQuestionMark,
                            text = uiText(UiText.Toolbar.HelpItem),
                        )
                        MenuFlyoutSeparator()
                        MenuFlyoutButton(
                            onClick = {
                                openExternalLink("https://github.com/SpartanB312/Grunt/issues")
                                isFlyoutVisible = false
                            },
                            icon = Icons.Default.Bug,
                            text = uiText(UiText.Toolbar.SubmitBugReport),
                        )
                        MenuFlyoutButton(
                            onClick = {
                                openExternalLink("https://github.com/SpartanB312/Grunt/issues")
                                isFlyoutVisible = false
                            },
                            icon = Icons.Default.ChatHelp,
                            text = uiText(UiText.Toolbar.SubmitFeatureRequest),
                        )
                        MenuFlyoutSeparator()
                        MenuFlyoutButton(
                            onClick = {
                                openExternalLink("https://github.com/SpartanB312/Grunt/releases")
                                isFlyoutVisible = false
                            },
                            icon = Icons.Default.Globe,
                            text = uiText(UiText.Toolbar.CheckForUpdates),
                        )
                        MenuFlyoutButton(
                            onClick = {
                                // TODO: built-in about me page
                                openExternalLink("https://github.com/SpartanB312/Grunt")
                                isFlyoutVisible = false
                            },
                            icon = Icons.Default.Info,
                            text = uiText(UiText.Toolbar.About),
                        )
                    }
                ) {
                    Text(uiText(UiText.Toolbar.Help))
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
                        contentDescription = uiText(UiText.Toolbar.Minimize),
                        buttonColors = ButtonDefaults.subtleButtonColors(),
                        onClick = onMinimize,
                    )
                    WindowControlButton(
                        icon = if (isMaximized) Icons.Default.SquareMultiple else Icons.Default.Square,
                        contentDescription = if (isMaximized) {
                            uiText(UiText.Toolbar.Restore)
                        } else {
                            uiText(UiText.Toolbar.Maximize)
                        },
                        buttonColors = ButtonDefaults.subtleButtonColors(),
                        onClick = onToggleMaximize,
                    )
                    val defaultSubtle = ButtonDefaults.subtleButtonColors()
                    WindowControlButton(
                        icon = Icons.Default.Dismiss,
                        contentDescription = uiText(UiText.Toolbar.Close),
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
                                page.displayText(),
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

private fun AppPage.displayText(): String {
    return when (this) {
        AppPage.General -> uiText(UiText.Toolbar.GeneralTab)
        AppPage.Editor -> uiText(UiText.Toolbar.EditorTab)
        AppPage.Native -> uiText(UiText.Toolbar.NativeTab)
        AppPage.Obfuscation -> uiText(UiText.Toolbar.ObfuscationTab)
        AppPage.Settings -> uiText(UiText.Toolbar.SettingsTab)
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
