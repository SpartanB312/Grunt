package net.spartanb312.grunteon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.spartanb312.grunteon.obfuscator.ObfConfig
import net.spartanb312.grunteon.obfuscator.TransformerEntry
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
    uiState: UIState,
    obfConfigState: MutableState<ObfConfig>
) {
    val state = remember { PipelineEditorState(uiState, obfConfigState) }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TransformerLibrary(
            state = state,
            modifier = Modifier.weight(1f)
        )
        PipelineStackPanel(
            state = state,
            modifier = Modifier.weight(1.25f)
        )
        Inspector(
            state,
            modifier = Modifier.weight(1.5f)
        )
    }
}
