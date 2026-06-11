package net.spartanb312.grunteon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.*

@Composable
fun Header(
    nodeCount: Int,
    enabledCount: Int,
    warningCount: Int,
    status: String,
    onReload: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Pipeline Editor",
                color = UiTextPrimary(),
                style = FluentTheme.typography.title,
                fontWeight = FontWeight.Bold
            )
            Text(
                "$enabledCount enabled / $nodeCount nodes. $warningCount order warnings.",
                color = if (warningCount == 0) UiTextSecondary() else UiWarningColor(),
                style = FluentTheme.typography.body
            )
        }
        Text(status, color = UiTextSecondary(), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    PanelSurface(modifier) {
        Column(Modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text("Transformer Library", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            UiTextField(
                value = search,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = "Search",
            )
            Spacer(Modifier.height(8.dp))
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
                            color = UiTextSecondary(),
                            style = FluentTheme.typography.bodyStrong,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
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
    val labelColor = when {
        definition.isHidden -> UiWarningColor()
        definition.isPluginProvided -> UiAccentColor()
        else -> UiTextPrimary()
    }
    SectionSurface(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(definition.label, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(definition.description, color = UiTextSecondary(), maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    PanelSurface(modifier) {
        Column(Modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pipeline Stack", fontWeight = FontWeight.Bold)
                    Text("Execution order is top to bottom. Duplicate transformers are allowed.")
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
    val borderColor = when {
        warning != null -> UiWarningColor()
        selected -> UiAccentColor()
        else -> UiBorderColor()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 118.dp)
            .clip(UiPanelShape)
            .background(if (selected) UiSelectedPanelColor() else UiSectionColor())
            .border(BorderStroke(1.dp, borderColor), UiPanelShape)
            .clickable(onClick = onSelect)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                UiIconButton(
                    imageVector = Icons.Default.ArrowSortUp,
                    contentDescription = "Move up",
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(32.dp)
                )
                io.github.composefluent.component.Icon(
                    imageVector = Icons.Default.ReOrderDotsVertical,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                UiIconButton(
                    imageVector = Icons.Default.ArrowSortDown,
                    contentDescription = "Move down",
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                "#${index + 1}",
                color = UiTextSecondary(),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 26.dp)
            )
            Column(Modifier.weight(1f).padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(definition?.label ?: node.config::class.simpleName.orEmpty(), fontWeight = FontWeight.SemiBold)
                    Text(definition?.category?.name ?: "Unknown", color = UiTextSecondary())
                }
                if (warning != null) {
                    Text(warning, color = UiWarningColor(), style = FluentTheme.typography.caption)
                } else {
                    Text(
                        definition?.description ?: node.config::class.qualifiedName.orEmpty(),
                        color = UiTextSecondary()
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.height(90.dp)
            ) {
                UiSwitch(checked = node.config.enabled, onCheckedChange = onEnabledChange)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiIconButton(
                        imageVector = Icons.Default.CopyAdd,
                        contentDescription = "Duplicate",
                        onClick = onDuplicate,
                        modifier = Modifier.size(32.dp)
                    )
                    UiIconButton(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VirtualMappingApplier() {
    NestedSurface(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("MappingApplier", color = UiTextSecondary(), fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(10.dp))
            Text("auto inserted after the last renamer source", color = UiTextSecondary())
        }
    }
}
