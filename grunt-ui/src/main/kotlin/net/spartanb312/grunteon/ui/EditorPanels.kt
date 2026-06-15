package net.spartanb312.grunteon.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.onClick
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Layer
import io.github.composefluent.component.*
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.*
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.TransformerEntry
import java.util.*

private data class DragCardBounds(
    val top: Float,
    val bottom: Float,
) {
    val center: Float get() = (top + bottom) / 2f
    val height: Float get() = bottom - top
}

private class PipelineDragState {
    private val cardBounds = mutableStateMapOf<Int, DragCardBounds>()

    var draggedIndex by mutableStateOf<Int?>(null)
        private set
    var hoveredIndex by mutableStateOf<Int?>(null)
        private set
    var dragOffsetY by mutableFloatStateOf(0f)
        private set
    val isDragging: Boolean
        get() = draggedIndex != null

    fun onCardMeasured(index: Int, top: Float, height: Float) {
        cardBounds[index] = DragCardBounds(top, top + height)
        if (draggedIndex != null) updateHoveredIndex()
    }

    fun onCardDisposed(index: Int) {
        cardBounds.remove(index)
    }

    fun startDrag(index: Int) {
        draggedIndex = index
        hoveredIndex = index
        dragOffsetY = 0f
    }

    fun dragBy(deltaY: Float) {
        if (draggedIndex == null) return
        dragOffsetY += deltaY
        updateHoveredIndex()
    }

    fun finishDrag(onMove: (fromIndex: Int, toIndex: Int) -> Unit) {
        val fromIndex = draggedIndex
        val toIndex = hoveredIndex
        reset()
        if (fromIndex != null && toIndex != null && fromIndex != toIndex) {
            onMove(fromIndex, toIndex)
        }
    }

    fun cancelDrag() {
        reset()
    }

    fun previewOffsetFor(index: Int): Float {
        val draggedIndex = draggedIndex ?: return 0f
        val hoveredIndex = hoveredIndex ?: return 0f
        if (index == draggedIndex || draggedIndex == hoveredIndex) return 0f

        val slotDistance = cardBounds[draggedIndex]?.slotDistance(draggedIndex, cardBounds) ?: return 0f
        return when {
            draggedIndex < hoveredIndex && index in (draggedIndex + 1)..hoveredIndex -> -slotDistance
            hoveredIndex < draggedIndex && index in hoveredIndex..<draggedIndex -> slotDistance
            else -> 0f
        }
    }

    private fun updateHoveredIndex() {
        val draggedIndex = draggedIndex ?: return
        val draggedBounds = cardBounds[draggedIndex] ?: return
        val draggedCenter = draggedBounds.center + dragOffsetY
        hoveredIndex = cardBounds
            .minByOrNull { (_, bounds) -> kotlin.math.abs(bounds.center - draggedCenter) }
            ?.key
            ?: draggedIndex
    }

    private fun reset() {
        draggedIndex = null
        hoveredIndex = null
        dragOffsetY = 0f
    }

    private fun DragCardBounds.slotDistance(
        index: Int,
        boundsMap: Map<Int, DragCardBounds>,
    ): Float {
        boundsMap[index + 1]?.let { next -> return next.top - top }
        boundsMap[index - 1]?.let { previous -> return top - previous.top }
        return height
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
            TextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp),
                placeholder = { Text("Search") },
                singleLine = true,
            )
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
    Category.Native -> Icons.Regular.Accessibility
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
                imageVector = Icons.Default.CircleSmall,
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
    val dragState = remember { PipelineDragState() }
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
                    TransformerCard(
                        state = state,
                        orderWarnings = orderWarnings,
                        index = index,
                        entry = entry,
                        dragState = dragState
                    )
                    if (index == mappingApplierPosition) {
                        VirtualMappingApplier()
                    }
                }
            }
        }
    }
}

@Composable
private fun TransformerCard(
    state: PipelineEditorState,
    orderWarnings: Map<Int, List<String>>,
    index: Int,
    entry: TransformerEntry,
    dragState: PipelineDragState,
) {
    val selected = state.selectedIndex == index
    val definition = findDefinition(entry.config, state.definitions)
    val warnings = orderWarnings[index]
    val isDragged = dragState.draggedIndex == index
    val isDropTarget = dragState.hoveredIndex == index && !isDragged
    val motionSpec = if (dragState.isDragging) {
        spring<Float>(stiffness = 650f, dampingRatio = 0.82f)
    } else {
        snap()
    }
    val previewOffset by animateFloatAsState(
        targetValue = if (isDragged) 0f else dragState.previewOffsetFor(index),
        animationSpec = motionSpec,
        label = "pipelineCardPreviewOffset"
    )
    val dragScale by animateFloatAsState(
        targetValue = if (isDragged) 1.03f else 1f,
        animationSpec = motionSpec,
        label = "pipelineCardScale"
    )
    val dragAlpha by animateFloatAsState(
        targetValue = if (isDragged) 0.96f else 1f,
        animationSpec = motionSpec,
        label = "pipelineCardAlpha"
    )
    val borderColor = when {
        warnings != null -> FluentTheme.colors.system.caution
        isDropTarget -> FluentTheme.colors.fillAccent.default
        selected -> FluentTheme.colors.fillAccent.default
        else -> FluentTheme.colors.stroke.card.default
    }

    DisposableEffect(index) {
        onDispose { dragState.onCardDisposed(index) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) FluentTheme.colors.background.card.tertiary else FluentTheme.colors.background.card.default,
                FluentTheme.shapes.control
            )
            .height(96.dp)
            .border(BorderStroke(2.dp, borderColor), FluentTheme.shapes.control)
            .onGloballyPositioned {
                dragState.onCardMeasured(index, it.positionInParent().y, it.size.height.toFloat())
            }
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer {
                translationY = if (isDragged) dragState.dragOffsetY else previewOffset
                scaleX = dragScale
                scaleY = dragScale
                alpha = dragAlpha
            }
            .clickable(onClick = { state.selectedIndex = index }),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(48.dp)
                    .pointerInput(index) {
                        detectDragGestures(
                            onDragStart = { _: Offset ->
                                state.selectedIndex = index
                                dragState.startDrag(index)
                            },
                            onDragEnd = {
                                dragState.finishDrag(state::moveTransformer)
                            },
                            onDragCancel = {
                                dragState.cancelDrag()
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            dragState.dragBy(dragAmount.y)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ReOrderDotsVertical,
                    contentDescription = "Drag to reorder",
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1.0f)
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "#${index}",
                        color = FluentTheme.colors.text.text.secondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        definition?.label ?: entry.config::class.simpleName.orEmpty(),
                        style = FluentTheme.typography.bodyLarge
                    )
                }

                if (warnings != null) {
                    Text(
                        warnings.joinToString("\n"),
                        style = FluentTheme.typography.caption.copy(color = FluentTheme.colors.system.caution),
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        definition?.description ?: entry.config::class.qualifiedName.orEmpty(),
                        style = FluentTheme.typography.caption,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight(),
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
