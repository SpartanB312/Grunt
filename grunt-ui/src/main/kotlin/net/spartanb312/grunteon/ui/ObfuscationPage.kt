package net.spartanb312.grunteon.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text

@Composable
fun ObfuscationPage(
    logs: List<String>,
    running: Boolean,
    onObfuscate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(logs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    PanelSurface(modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Obfuscation", style = FluentTheme.typography.title, fontWeight = FontWeight.Bold)
                Text("Output information from work instance", color = FluentTheme.colors.text.text.secondary)
            }
            NestedSurface(Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            "No obfuscation run yet.",
                            color = FluentTheme.colors.text.text.secondary, fontFamily = FontFamily.Monospace
                        )
                    } else {
                        logs.forEach { line ->
                            Text(line, fontFamily = FontFamily.Monospace, color = FluentTheme.colors.text.text.primary)
                        }
                    }
                }
            }
            SectionSurface(Modifier.fillMaxWidth().height(120.dp)) {
                Box(Modifier.fillMaxSize().padding(12.dp)) {
                    Text("Reserved", color = FluentTheme.colors.text.text.secondary)
                    Row(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (running) Text("Running...", color = FluentTheme.colors.text.text.secondary)
                        UiButton(onClick = onObfuscate, enabled = !running) {
                            Text("Obfuscate")
                        }
                    }
                }
            }
        }
    }
}
