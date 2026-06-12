package net.spartanb312.grunteon.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.composefluent.component.Button
import io.github.composefluent.component.ButtonDefaults
import io.github.composefluent.component.Text

private val ToolbarTabWidth = 128.dp

@Composable
fun TopToolbar(uiState: UIState) {
    Row(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .height(32.dp)
            .padding(all = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.fillMaxHeight().padding(start = 2.dp)) {
            GrunteonLogo(Modifier.align(Alignment.Center))
        }
        AppPage.entries.forEach {
            ToolbarTab(
                label = it.name,
                selected = uiState.currentPage == it,
                onClick = { uiState.currentPage = it }
            )
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
