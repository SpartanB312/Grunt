package net.spartanb312.grunteon.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.composefluent.component.*

private val ToolbarTabWidth = 128.dp

@Composable
fun TopToolbar(uiState: UIState) {
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
