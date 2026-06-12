package net.spartanb312.grunteon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.onClick
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Layer
import io.github.composefluent.component.*
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.*
import net.spartanb312.grunteon.obfuscator.TransformerEntry
import net.spartanb312.grunteon.obfuscator.process.Category
import java.util.*

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
                color = FluentTheme.colors.text.text.primary,
                style = FluentTheme.typography.title,
                fontWeight = FontWeight.Bold
            )
            Text(
                "$enabledCount enabled / $nodeCount nodes. $warningCount order warnings.",
                color = if (warningCount == 0) FluentTheme.colors.text.text.secondary else FluentTheme.colors.system.caution,
                style = FluentTheme.typography.body
            )
        }
        Text(status, color = FluentTheme.colors.text.text.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        UiOutlinedButton(onClick = onReload) { Text("Reload") }
        UiButton(onClick = onSave) { Text("Save config") }
    }
}

@Composable
fun TransformerLibrary(
    state: PipelineEditorState,
    modifier: Modifier = Modifier,
) {
    PanelSurface(
        title = "Transformer Library",
        description = "Browse available transformers and add them to the pipeline stack.",
        modifier
    ) {
        var search by remember { mutableStateOf("") }
        Column(
            modifier = Modifier
                .padding(horizontal = 13.dp)
        ) {
            TextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = (1 * 24).dp),
                placeholder = { Text("Search") },
                singleLine = true,
            )
        }
        val visibleDefinitions = state.definitions.filterNot { it.isHidden }
        val filtered = remember(search) {
            visibleDefinitions.filter {
                search.isBlank() ||
                    it.label.contains(search, ignoreCase = true) ||
                    it.category.name.contains(search, ignoreCase = true)
            }
        }
        val listState = rememberLazyListState()
        ScrollbarContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp, 8.dp, 0.dp, 8.dp),
            adapter = rememberScrollbarAdapter(listState),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(FluentTheme.shapes.control)
                    .padding(0.dp, 0.dp, 12.dp, 0.dp),
            ) {
                filtered.groupByTo(EnumMap(Category::class.java)) { it.category }
                    .forEach { (category, categoryDefinitions) ->
                        item {
                            var visible by remember { mutableStateOf(false) }
                            Expander(
                                visible,
                                { visible = it },
                                icon = {
                                    Icon(
                                        imageVector = categoryToIcon(category),
                                        contentDescription = null
                                    )
                                },
                                heading = {
                                    Text(
                                        category.name,
                                        style = FluentTheme.typography.bodyStrong,
                                    )
                                }
                            ) {
                                Layer {
                                    Column {
                                        categoryDefinitions.forEach { definition ->
                                            LibraryItem(state, definition)
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
}

fun categoryToIcon(category: Category): ImageVector = when (category) {
    Category.Encryption -> Icons.Regular.LockClosed
    Category.Controlflow -> Icons.Regular.Flowchart
    Category.AntiDebug -> Icons.Regular.Bug
    Category.Authentication -> Icons.Regular.Fingerprint
    Category.Exploit -> Icons.Regular.TargetArrow
    Category.Miscellaneous -> Icons.Regular.MoreCircle
    Category.Optimization -> Icons.Regular.FlashCheckmark
    Category.Redirect -> Icons.Regular.Router
    Category.Renaming -> Icons.Regular.TextChangeCase
    Category.Other -> Icons.Regular.PuzzlePiece
    Category.PostProcess -> Icons.Regular.WrenchScrewdriver
}

@Composable
private fun LibraryItem(state: PipelineEditorState, definition: TransformerDefinition) {
    val labelColor = when {
        definition.isHidden -> FluentTheme.colors.system.caution
        definition.isPluginProvided -> FluentTheme.colors.fillAccent.default
        else -> FluentTheme.colors.text.text.primary
    }
    CardExpanderItem(
        icon = {
            Icon(
                imageVector = Icons.Default.Circle,
                contentDescription = null
            )
        },
        heading = {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
            ) {
                Text(
                    definition.label,
                    style = FluentTheme.typography.body.copy(color = labelColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    definition.description,
                    style = FluentTheme.typography.caption,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    ) {
        Button(
            onClick = { state.addTransformerAfterSelection(definition) },
            modifier = Modifier
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add transformer"
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PipelineStackPanel(
    state: PipelineEditorState,
    modifier: Modifier = Modifier,
) {
    PanelSurface(
        title = "Pipeline Stack",
        description = "Execution order is top to bottom. Duplicate transformers are allowed.",
        modifier = modifier
    ) {
        val listState = rememberLazyListState()
        val mappingApplierPosition = remember(state.transformerProperty) {
            state.transformerList.indexOfLast {
                findDefinition(it.config, state.definitions)?.transformerPrototype?.category == Category.Renaming
            }
        }

        val orderWarnings = remember(state.transformerProperty) {
            validateOrder(state.transformerList, state.definitions)
        }
        ScrollbarContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp, 8.dp, 0.dp, 8.dp),
            adapter = rememberScrollbarAdapter(listState)
        ) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(FluentTheme.shapes.control)
                    .padding(0.dp, 0.dp, 12.dp, 0.dp)
                    .onClick { state.selectedIndex = -1 }
            ) {
                itemsIndexed(state.transformerList) { index, entry ->
                    PipelineNodeCard(state, orderWarnings, index, entry)
                    if (index == mappingApplierPosition) {
                        VirtualMappingApplier()
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelineNodeCard(
    state: PipelineEditorState,
    orderWarnings: Map<Int, List<String>>,
    index: Int,
    entry: TransformerEntry,
) {
    val selected = state.selectedIndex == index
    val definition = findDefinition(entry.config, state.definitions)
    val warnings = orderWarnings[index]
    val borderColor = when {
        warnings != null -> FluentTheme.colors.system.caution
        selected -> FluentTheme.colors.fillAccent.default
        else -> FluentTheme.colors.stroke.card.default
    }

    val canMoveUp = index > 0
    val canMoveDown = index < state.transformerList.size - 1

    Box(
        modifier = Modifier
            .background(
                if (selected) FluentTheme.colors.background.card.tertiary else FluentTheme.colors.background.card.default,
                FluentTheme.shapes.control
            )
            .border(BorderStroke(2.dp, borderColor), FluentTheme.shapes.control)
            .clickable(onClick = { state.selectedIndex = index }),
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1.0f)
                    .fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    UiIconButton(
                        imageVector = Icons.Default.ArrowSortUp,
                        contentDescription = "Move up",
                        onClick = { state.moveTransformer(index, index - 1) },
                        enabled = canMoveUp,
                        modifier = Modifier.size(32.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.ReOrderDotsVertical,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    UiIconButton(
                        imageVector = Icons.Default.ArrowSortDown,
                        contentDescription = "Move down",
                        onClick = { state.moveTransformer(index, index + 1) },
                        enabled = canMoveDown,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    "#${index + 1}",
                    color = FluentTheme.colors.text.text.secondary,
                    fontFamily = FontFamily.Monospace
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        definition?.label ?: entry.config::class.simpleName.orEmpty(),
                        style = FluentTheme.typography.bodyLarge
                    )
                    if (warnings != null) {
                        warnings.forEach { warning ->
                            Text(
                                warning,
                                style = FluentTheme.typography.caption.copy(color = FluentTheme.colors.system.caution)
                            )
                        }
                    } else {
                        Text(
                            definition?.description ?: entry.config::class.qualifiedName.orEmpty(),
                            style = FluentTheme.typography.caption,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.height(100.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Text(
                    definition?.category?.name ?: "Unknown",
                    color = FluentTheme.colors.text.text.tertiary,
                )
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Switcher(
                        checked = entry.enabled,
                        onCheckStateChange = { state.transformerList[index] = entry.copy(enabled = it) },
                        text = null
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { state.addTransformerEntryAfterSelection(entry) },
                            iconOnly = true
                        ) {
                            Icon(imageVector = Icons.Default.CopyAdd, contentDescription = "Duplicate")
                        }
                        Button(
                            onClick = { state.transformerList.removeAt(index) },
                            iconOnly = true
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualMappingApplier() {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(color = FluentTheme.colors.background.layer.default)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Mapping applier inserted automatically after the last renamer.",
                color = FluentTheme.colors.text.text.secondary
            )
        }
    }
}
