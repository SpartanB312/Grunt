package net.spartanb312.grunteon.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.*
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.*

private val ToolbarTabWidth = 128.dp

@Composable
fun TopToolbar(
    uiState: UIState,
    onNewConfig: () -> Unit,
    onOpenConfig: () -> Unit,
    onSaveConfig: () -> Boolean,
    onSaveConfigAs: () -> Unit,
    onExit: () -> Unit,
) {
    Column {
        MenuBar(
            modifier = Modifier
                .background(color = FluentTheme.colors.background.mica.base)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 0.dp)
        ) {
            GrunteonLogo(modifier = Modifier.height(32.dp))
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

@Composable
private fun GrunteonLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource("logo.svg"),
        contentDescription = "Grunteon",
        modifier = modifier,
    )
}

@Composable
private fun ToolbarTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(ToolbarTabWidth),
        buttonColors = if (selected) ButtonDefaults.accentButtonColors() else ButtonDefaults.buttonColors(),
    ) {
        Text(label, textAlign = TextAlign.Center)
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
