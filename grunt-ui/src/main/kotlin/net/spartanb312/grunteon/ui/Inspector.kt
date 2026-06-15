package net.spartanb312.grunteon.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.ScrollbarContainer
import io.github.composefluent.component.rememberScrollbarAdapter

@Composable
fun Inspector(
    state: PipelineEditorState,
    modifier: Modifier = Modifier,
) {
    val selected = state.selectedIndex
    val entry = state.transformerList.getOrNull(selected)
    val definition = entry?.let { findDefinition(entry.config, state.definitions) }
    val transformerName = definition?.label ?: entry?.config?.let { it::class.simpleName }
    val transformerDesc = definition?.description ?: entry?.config?.let { it::class.qualifiedName }
    PanelSurface(
        if (transformerName == null) "Inspector" else "Inspector - $transformerName",
        transformerDesc ?: "Select a transformer node to edit its Config.",
        modifier
    ) {
        if (entry == null) return@PanelSurface
        val scrollState = rememberScrollState()
        ScrollbarContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp, 8.dp, 0.dp, 8.dp),
            adapter = rememberScrollbarAdapter(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(FluentTheme.shapes.control)
                    .padding(0.dp, 0.dp, 12.dp, 0.dp)
                    .verticalScroll(scrollState),
            ) {
                ConfigEditor(
                    value = entry.config,
                    onChange = {
                        state.transformerList[selected] = state.transformerList[selected].copy(config = it)
                    },
                )
            }
        }
    }
}


