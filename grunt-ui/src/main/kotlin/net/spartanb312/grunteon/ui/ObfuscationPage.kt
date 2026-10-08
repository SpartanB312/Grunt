package net.spartanb312.grunteon.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.ScrollbarContainer
import io.github.composefluent.component.Text
import io.github.composefluent.component.rememberScrollbarAdapter
import io.github.composefluent.surface.Card

@Composable
fun ObfuscationPage(
    logs: List<UiLogEntry>,
    fullLogPath: String?,
    running: Boolean,
    enabledTransformerCount: Int,
    nativePipelineEnabled: Boolean,
    onObfuscate: () -> Unit,
    ready: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberLazyListState()
    val followState = remember { LogTailFollowState() }
    val enabledTransformersText = if (nativePipelineEnabled) {
        uiText(UiText.Obfuscation.EnabledTransformersWithNative, "count" to enabledTransformerCount)
    } else {
        uiText(UiText.Obfuscation.EnabledTransformers, "count" to enabledTransformerCount)
    }

    LaunchedEffect(scrollState) {
        snapshotFlow {
            Triple(scrollState.isScrollInProgress, scrollState.canScrollForward, followState.isAutomaticScroll)
        }.collect { (scrolling, forward, automatic) ->
            followState.onScroll(scrolling, forward, automatic)
        }
    }
    LaunchedEffect(logs.lastOrNull()?.sequence) {
        if (logs.isEmpty()) followState.reset()
        else if (followState.followsTail && !scrollState.isScrollInProgress) {
            followState.isAutomaticScroll = true
            try {
                scrollState.scrollToItem(logs.lastIndex)
                val layout = scrollState.layoutInfo
                val last = layout.visibleItemsInfo.lastOrNull()
                if (last != null) {
                    val viewport = layout.viewportEndOffset - layout.viewportStartOffset
                    scrollState.scrollToItem(logs.lastIndex, (last.size - viewport).coerceAtLeast(0))
                }
            } finally {
                followState.isAutomaticScroll = false
            }
        }
    }

    PanelSurface(modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (fullLogPath != null) {
                Text(uiText(UiText.Obfuscation.LogTail, "path" to fullLogPath),
                    color = FluentTheme.colors.text.text.secondary)
            }
            Card(
                Modifier.weight(1.0f)
            ) {
                ScrollbarContainer(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp, 8.dp, 0.dp, 8.dp),
                    adapter = rememberScrollbarAdapter(scrollState),
                ) {
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (logs.isEmpty()) {
                            item {
                                Text(
                                    uiText(UiText.Obfuscation.NoRunYet),
                                    color = FluentTheme.colors.text.text.secondary, fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            items(logs, key = { it.sequence }) { line ->
                                Text(
                                    line.text,
                                    fontFamily = FontFamily.Monospace,
                                    color = FluentTheme.colors.text.text.primary
                                )
                            }
                        }
                    }
                }
            }
            SectionSurface(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                Box(Modifier.fillMaxSize().padding(12.dp)) {
                    Text(enabledTransformersText, color = FluentTheme.colors.text.text.secondary)
                    Row(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (running) Text(uiText(UiText.Obfuscation.Running), color = FluentTheme.colors.text.text.secondary)
                        UiButton(onClick = onObfuscate, enabled = !running && ready) {
                            Text(uiText(UiText.Obfuscation.Obfuscate))
                        }
                    }
                }
            }
        }
    }
}
