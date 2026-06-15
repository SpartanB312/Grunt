package net.spartanb312.grunteon.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.composefluent.component.Button
import io.github.composefluent.component.Icon
import io.github.composefluent.component.Text

@Composable
fun IconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    text: String? = null,
    contentDescription: String? = null,
) {
    Button(
        onClick = onClick,
        iconOnly = text == null,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
        )
        if (text != null) {
            Text(text)
        }
    }
}