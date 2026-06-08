package net.spartanb312.grunteon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun Header(
    nodeCount: Int,
    enabledCount: Int,
    warningCount: Int,
    status: String,
    onReload: () -> Unit,
    onSave: () -> Unit,
) {
    val palette = LocalUiPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Pipeline Editor",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "$enabledCount enabled / $nodeCount nodes. $warningCount order warnings.",
                color = if (warningCount == 0) palette.muted else palette.warning,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(status, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        UiOutlinedButton(onClick = onReload) { Text("Reload") }
        UiButton(onClick = onSave) { Text("Save config") }
    }
}

@Composable
fun TransformerLibrary(
    definitions: List<TransformerDefinition>,
    showHiddenTransformers: Boolean,
    search: String,
    onSearchChange: (String) -> Unit,
    onAdd: (TransformerDefinition) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalUiPalette.current
    PanelSurface(modifier) {
        Column(Modifier.fillMaxHeight().padding(12.dp)) {
            Text("Transformer Library", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = search,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search") },
            )
            Spacer(Modifier.height(10.dp))
            val visibleDefinitions = if (showHiddenTransformers) definitions else definitions.filterNot { it.isHidden }
            val filtered = visibleDefinitions.filter {
                search.isBlank() ||
                    it.label.contains(search, ignoreCase = true) ||
                    it.category.name.contains(search, ignoreCase = true)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filtered.groupBy { it.category }.forEach { (category, categoryDefinitions) ->
                    item {
                        Text(
                            category.name,
                            color = palette.muted,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(categoryDefinitions) { definition ->
                        LibraryItem(definition, onAdd)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryItem(definition: TransformerDefinition, onAdd: (TransformerDefinition) -> Unit) {
    val palette = LocalUiPalette.current
    val labelColor = when {
        definition.isHidden -> palette.warning
        definition.isPluginProvided -> Color(0xFF8FD4FF)
        else -> palette.text
    }
    SectionSurface(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(definition.label, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(definition.description, color = palette.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            UiOutlinedButton(onClick = { onAdd(definition) }) {
                Text("Add")
            }
        }
    }
}

@Composable
fun PipelineStack(
    nodes: MutableList<PipelineNode>,
    definitions: List<TransformerDefinition>,
    selectedNodeId: Long?,
    orderWarnings: Map<Long, String>,
    onSelect: (Long) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDuplicate: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onEnabledChange: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalUiPalette.current
    PanelSurface(modifier) {
        Column(Modifier.fillMaxHeight().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pipeline Stack", fontWeight = FontWeight.Bold)
                    Text("Execution order is top to bottom. Duplicate transformers are allowed.", color = palette.muted)
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(nodes.size) { index ->
                    val node = nodes[index]
                    val definition = findDefinition(node.config, definitions)
                    PipelineNodeCard(
                        index = index,
                        node = node,
                        definition = definition,
                        selected = node.id == selectedNodeId,
                        warning = orderWarnings[node.id],
                        canMoveUp = index > 0,
                        canMoveDown = index < nodes.lastIndex,
                        onSelect = { onSelect(node.id) },
                        onMoveUp = { onMove(index, index - 1) },
                        onMoveDown = { onMove(index, index + 1) },
                        onDuplicate = { onDuplicate(index) },
                        onDelete = { onDelete(index) },
                        onEnabledChange = { enabled -> onEnabledChange(node.id, enabled) },
                    )
                    if (isVirtualMappingApplierPosition(index, nodes, definitions)) {
                        VirtualMappingApplier()
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelineNodeCard(
    index: Int,
    node: PipelineNode,
    definition: TransformerDefinition?,
    selected: Boolean,
    warning: String?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    val palette = LocalUiPalette.current
    val borderColor = when {
        warning != null -> palette.warning
        selected -> palette.accent
        else -> palette.stroke
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = if (selected) palette.selectedPanel else palette.panelAlt),
        shape = UiPanelShape,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("#${index + 1}", color = palette.muted, fontFamily = FontFamily.Monospace)
                Column(Modifier.weight(1f)) {
                    Text(definition?.label ?: node.config::class.simpleName.orEmpty(), fontWeight = FontWeight.SemiBold)
                    Text(definition?.category?.name ?: "Unknown", color = palette.muted)
                }
                Switch(checked = node.config.enabled, onCheckedChange = onEnabledChange)
            }
            if (warning != null) {
                Text(warning, color = palette.warning, style = MaterialTheme.typography.bodySmall)
            } else {
                Text(definition?.description ?: node.config::class.qualifiedName.orEmpty(), color = palette.muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UiOutlinedButton(onClick = onMoveUp, enabled = canMoveUp) { Text("Up") }
                UiOutlinedButton(onClick = onMoveDown, enabled = canMoveDown) { Text("Down") }
                UiOutlinedButton(onClick = onDuplicate) { Text("Duplicate") }
                UiTextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun VirtualMappingApplier() {
    val palette = LocalUiPalette.current
    NestedSurface(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("MappingApplier", color = palette.muted, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(10.dp))
            Text("auto inserted after the last renamer source", color = palette.muted)
        }
    }
}
