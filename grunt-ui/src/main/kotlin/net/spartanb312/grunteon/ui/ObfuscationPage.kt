package net.spartanb312.grunteon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ObfuscationPage(
    logs: List<String>,
    running: Boolean,
    onObfuscate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalUiPalette.current
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
                Text("Obfuscation", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Output information from work instance", color = palette.muted)
            }
            NestedSurface(Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (logs.isEmpty()) {
                        Text("No obfuscation run yet.", color = palette.muted, fontFamily = FontFamily.Monospace)
                    } else {
                        logs.forEach { line ->
                            Text(line, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            SectionSurface(Modifier.fillMaxWidth().height(120.dp)) {
                Box(Modifier.fillMaxSize().padding(12.dp)) {
                    Text("Reserved", color = palette.muted)
                    Row(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (running) Text("Running...", color = palette.muted)
                        UiButton(onClick = onObfuscate, enabled = !running) {
                            Text("Obfuscate")
                        }
                    }
                }
            }
        }
    }
}
