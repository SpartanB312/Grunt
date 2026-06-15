package net.spartanb312.grunteon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.ButtonDefaults
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerEntry
import java.awt.Cursor
import kotlin.reflect.*
import kotlin.reflect.full.memberFunctions

class DataClassUpdater<S : Any, T : Any>(
    stateClazz: KClass<S>,
    private val stateGet: () -> S,
    private val stateSet: (S) -> Unit,
    private val property: KProperty1<S, T>,
) {
    constructor(
        stateClazz: KClass<S>,
        state: MutableState<S>,
        property: KProperty1<S, T>
    ) : this(
        stateClazz,
        state::value,
        { state.value = it },
        property
    )

    private val copyFunc: KFunction<S>
    private val parameter: KParameter

    init {
        require(stateClazz.isData) { "Only data classes are supported" }
        @Suppress("UNCHECKED_CAST")
        copyFunc = checkNotNull(stateClazz.memberFunctions.find { it.name == "copy" } as? KFunction<S>) {
            "Data class must have a copy function"
        }
        check(copyFunc.returnType.classifier == stateClazz) { "Copy function must return the same type as the data class $stateClazz" }
        parameter = checkNotNull(copyFunc.parameters.find { it.name == property.name }) {
            "Copy function must have a parameter for the property"
        }
    }

    var value
        get() = property.get(stateGet())
        set(newValue) {
            val current = stateGet()
            val args = mapOf(
                copyFunc.parameters[0] to current, // this
                parameter to newValue
            )
            stateSet(copyFunc.callBy(args))
        }

    operator fun getValue(thisRef: Any?, dummy: KProperty<*>): T {
        return value
    }

    operator fun setValue(thisRef: Any?, dummy: KProperty<*>, newValue: T) {
        value = newValue
    }

    companion object {
        inline operator fun <reified S : Any, reified T : Any> invoke(
            state: MutableState<S>,
            property: KProperty1<S, T>
        ) = DataClassUpdater(S::class, state, property)

        inline operator fun <reified S : Any, reified T : Any> invoke(
            noinline stateSet: (S) -> Unit,
            noinline stateGet: () -> S,
            property: KProperty1<S, T>
        ) = DataClassUpdater(S::class, stateGet, stateSet, property)
    }
}

class ListUpdater<E>(
    val valueGet: () -> List<E>,
    val valueSet: (List<E>) -> Unit
) : MutableList<E> {
    constructor(dataClassUpdater: DataClassUpdater<*, List<E>>) : this(
        valueGet = { dataClassUpdater.value },
        valueSet = { dataClassUpdater.value = it }
    )

    var value
        get() = valueGet()
        set(newValue) = valueSet(newValue)


    override val size: Int
        get() = value.size

    override fun add(element: E): Boolean {
        val newList = value + element
        value = newList
        return true
    }

    override fun remove(element: E): Boolean {
        val newList = value - element
        val removed = newList.size != value.size
        value = newList
        return removed
    }

    override fun addAll(elements: Collection<E>): Boolean {
        val newList = value + elements
        val changed = newList.size != value.size
        value = newList
        return changed
    }

    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        val newList = MutableList(index) {
            value[it]
        }
        newList.addAll(elements)
        newList.addAll(value.subList(index, value.size))
        value = newList
        return true
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        val newList = value - elements
        val changed = newList.size != value.size
        value = newList
        return changed
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        val newList = value.filter { it in elements }
        val changed = newList.size != value.size
        value = newList
        return changed
    }

    override fun clear() {
        value = emptyList()
    }

    override fun set(index: Int, element: E): E {
        val current = value
        val oldElement = current[index]
        val newList = current.toMutableList().also { it[index] = element }
        value = newList
        return oldElement
    }

    override fun add(index: Int, element: E) {
        val current = value
        val newList = current.toMutableList().also { it.add(index, element) }
        value = newList
    }

    override fun removeAt(index: Int): E {
        val current = value
        val oldElement = current[index]
        val newList = current.toMutableList().also { it.removeAt(index) }
        value = newList
        return oldElement
    }

    override fun listIterator(): MutableListIterator<E> {
        return Iterator(value.listIterator())
    }

    override fun listIterator(index: Int): MutableListIterator<E> {
        return Iterator(value.listIterator(index))
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> {
        throw UnsupportedOperationException("SubList is not supported")
    }

    override fun isEmpty(): Boolean {
        return value.isEmpty()
    }

    override fun contains(element: E): Boolean {
        return value.contains(element)
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        return value.containsAll(elements)
    }

    override fun get(index: Int): E {
        return value[index]
    }

    override fun indexOf(element: E): Int {
        return value.indexOf(element)
    }

    override fun lastIndexOf(element: E): Int {
        return value.lastIndexOf(element)
    }

    override fun iterator(): MutableIterator<E> {
        return Iterator(value.listIterator())
    }

    private class Iterator<E>(val raw: ListIterator<E>) : ListIterator<E> by raw, MutableListIterator<E> {
        override fun remove() {
            throw UnsupportedOperationException("Setting elements through iterator is not supported")
        }

        override fun set(element: E) {
            throw UnsupportedOperationException("Setting elements through iterator is not supported")
        }

        override fun add(element: E) {
            throw UnsupportedOperationException("Adding elements through iterator is not supported")
        }
    }
}

class PipelineEditorState(
    val uiState: UIState,
    obfConfigState: MutableState<ObfConfig>
) {
    val definitions = transformerDefinitions()
    var selectedIndexState by mutableStateOf(-1)
    var selectedIndex: Int
        get() {
            if (selectedIndexState !in transformerList.indices) selectedIndexState = -1
            return selectedIndexState
        }
        set(value) {
            selectedIndexState = if (value !in transformerList.indices) {
                -1
            } else {
                value
            }
        }

    val dataClassUpdater = DataClassUpdater(obfConfigState, ObfConfig::transformers)
    var transformerProperty by dataClassUpdater
    val transformerList = ListUpdater(dataClassUpdater)

    fun addTransformerEntry(index: Int, newEntry: TransformerEntry) {
        require(index in -1..transformerList.size) { "Index out of bounds: $index" }
        val currTransformers = transformerList
        val insertIndex = if (index == -1) currTransformers.size else index
        transformerList.add(insertIndex, newEntry)
        selectedIndex = insertIndex
        uiState.globalStatus = "Added ${newEntry.name}"
    }

    fun addTransformerEntryAfterSelection(newEntry: TransformerEntry) {
        addTransformerEntry(if (selectedIndex != -1) selectedIndex + 1 else -1, newEntry)
    }

    fun addTransformer(index: Int, definition: TransformerDefinition) {
        addTransformerEntry(index, TransformerEntry(name = definition.label, config = definition.configFactory()))
    }

    fun addTransformerAfterSelection(definition: TransformerDefinition) {
        addTransformer(if (selectedIndex != -1) selectedIndex + 1 else -1, definition)
    }

    fun moveTransformer(fromIndex: Int, toIndex: Int) {
        val current = transformerProperty
        require(fromIndex in current.indices) { "From index out of bounds: $fromIndex" }
        require(toIndex in current.indices) { "To index out of bounds: $toIndex" }
        if (fromIndex == toIndex) return

        val moved = current[fromIndex]
        val newList = current.toMutableList().also {
            it.removeAt(fromIndex)
            it.add(toIndex, moved)
        }
        transformerProperty = newList
        selectedIndex = when {
            selectedIndex == fromIndex -> toIndex
            fromIndex < toIndex && selectedIndex in (fromIndex + 1)..toIndex -> selectedIndex - 1
            toIndex < fromIndex && selectedIndex in toIndex..<fromIndex -> selectedIndex + 1
            else -> selectedIndex
        }
    }
}

@Composable
fun PipelineEditorPage(
    state: PipelineEditorState,
) {
    val initialTotalWeight = 1f + 1.25f + 1.5f
    var libraryWeight by remember { mutableFloatStateOf(1f / initialTotalWeight) }
    var stackWeight by remember { mutableFloatStateOf(1.25f / initialTotalWeight) }
    val handleWidth = 12.dp
    val minPanelWidth = 280.dp
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val totalPanelWidthPx = remember(maxWidth, density) {
            with(density) { (maxWidth.toPx() - handleWidth.toPx() * 2).coerceAtLeast(1f) }
        }
        val minWeight = remember(maxWidth, density) {
            with(density) { (minPanelWidth.toPx() / totalPanelWidthPx).coerceAtMost(0.3f) }
        }

        LaunchedEffect(minWeight) {
            val normalizedLibrary = libraryWeight.coerceIn(minWeight, 1f - 2 * minWeight)
            val normalizedStack = stackWeight.coerceIn(minWeight, 1f - normalizedLibrary - minWeight)
            if (normalizedLibrary != libraryWeight) libraryWeight = normalizedLibrary
            if (normalizedStack != stackWeight) stackWeight = normalizedStack
        }
        val rightWeight = (1f - libraryWeight - stackWeight).coerceAtLeast(minWeight)

        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            TransformerLibrary(
                state = state,
                modifier = Modifier
                    .weight(libraryWeight)
                    .fillMaxHeight()
            )
            PanelResizeHandle(
                width = handleWidth,
                onDrag = { deltaPx ->
                    val deltaWeight = deltaPx / totalPanelWidthPx
                    val pairTotal = libraryWeight + stackWeight
                    libraryWeight = (libraryWeight + deltaWeight)
                        .coerceIn(minWeight, pairTotal - minWeight)
                    stackWeight = pairTotal - libraryWeight
                }
            )
            PipelineStackPanel(
                state = state,
                modifier = Modifier
                    .weight(stackWeight)
                    .fillMaxHeight()
            )
            PanelResizeHandle(
                width = handleWidth,
                onDrag = { deltaPx ->
                    val deltaWeight = deltaPx / totalPanelWidthPx
                    stackWeight = (stackWeight + deltaWeight)
                        .coerceIn(minWeight, 1f - libraryWeight - minWeight)
                }
            )
            Inspector(
                state,
                modifier = Modifier
                    .weight(rightWeight)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun PanelResizeHandle(
    width: Dp,
    onDrag: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .hoverable(interactionSource = interactionSource)
            .pointerHoverIcon(icon = PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(onDrag) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false }
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val buttonColors = ButtonDefaults.buttonColors()
        val color = when {
            dragging -> buttonColors.pressed
            isHovered -> buttonColors.hovered
            else -> buttonColors.default
        }
        Box(
            modifier = Modifier
                .padding(4.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .graphicsLayer {
                    alpha = if (dragging) 1f else 0.65f
                }
                .background(
                    color = color.fillColor,
                    shape = FluentTheme.shapes.control
                )
        )
    }
}
