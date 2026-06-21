package net.spartanb312.grunteon.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text

@Composable
fun WelcomeScreen(
    status: String,
    onOpenConfig: () -> Unit,
    onNewConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.width(320.dp)
        ) {
            Text(
                "Grunteon",
                color = FluentTheme.colors.text.text.primary,
                style = FluentTheme.typography.title,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(status, color = FluentTheme.colors.text.text.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            UiButton(onClick = onOpenConfig, modifier = Modifier.fillMaxWidth()) {
                Text(uiText(UiText.Welcome.OpenExistingConfig))
            }
            UiOutlinedButton(onClick = onNewConfig, modifier = Modifier.fillMaxWidth()) {
                Text(uiText(UiText.Welcome.NewConfig))
            }
        }
    }
}
