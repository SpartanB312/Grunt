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

class DataClassListUpdater<E>(val updater: DataClassUpdater<*, List<E>>) : MutableList<E> {
    override val size: Int
        get() = updater.value.size

    override fun add(element: E): Boolean {
        val newList = updater.value + element
        updater.value = newList
        return true
    }

    override fun remove(element: E): Boolean {
        val newList = updater.value - element
        val removed = newList.size != updater.value.size
        updater.value = newList
        return removed
    }

    override fun addAll(elements: Collection<E>): Boolean {
        val newList = updater.value + elements
        val changed = newList.size != updater.value.size
        updater.value = newList
        return changed
    }

    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        val newList = MutableList(index) {
            updater.value[it]
        }
        newList.addAll(elements)
        newList.addAll(updater.value.subList(index, updater.value.size))
        updater.value = newList
        return true
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        val newList = updater.value - elements
        val changed = newList.size != updater.value.size
        updater.value = newList
        return changed
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        val newList = updater.value.filter { it in elements }
        val changed = newList.size != updater.value.size
        updater.value = newList
        return changed
    }

    override fun clear() {
        updater.value = emptyList()
    }

    override fun set(index: Int, element: E): E {
        val current = updater.value
        val oldElement = current[index]
        val newList = current.toMutableList().also { it[index] = element }
        updater.value = newList
        return oldElement
    }

    override fun add(index: Int, element: E) {
        val current = updater.value
        val newList = current.toMutableList().also { it.add(index, element) }
        updater.value = newList
    }

    override fun removeAt(index: Int): E {
        val current = updater.value
        val oldElement = current[index]
        val newList = current.toMutableList().also { it.removeAt(index) }
        updater.value = newList
        return oldElement
    }

    override fun listIterator(): MutableListIterator<E> {
        return Iterator(updater.value.listIterator())
    }

    override fun listIterator(index: Int): MutableListIterator<E> {
        return Iterator(updater.value.listIterator(index))
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> {
        throw UnsupportedOperationException("SubList is not supported")
    }

    override fun isEmpty(): Boolean {
        return updater.value.isEmpty()
    }

    override fun contains(element: E): Boolean {
        return updater.value.contains(element)
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        return updater.value.containsAll(elements)
    }

    override fun get(index: Int): E {
        return updater.value[index]
    }

    override fun indexOf(element: E): Int {
        return updater.value.indexOf(element)
    }

    override fun lastIndexOf(element: E): Int {
        return updater.value.lastIndexOf(element)
    }

    override fun iterator(): MutableIterator<E> {
        return Iterator(updater.value.listIterator())
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
    var selectedIndex by mutableStateOf(-1)
    val dataClassUpdater = DataClassUpdater(obfConfigState, ObfConfig::transformers)
    var transformerProperty by dataClassUpdater
    val transformerList = DataClassListUpdater(dataClassUpdater)

    fun addTransformerEntry(index: Int, newEntry: TransformerEntry) {
        require(index in -1..transformerList.size) { "Index out of bounds: $index" }
        val currTransformers = transformerList
        val insertIndex = if (index == -1) currTransformers.size else index
        transformerList.add(insertIndex, newEntry)
        selectedIndex = insertIndex
        uiState.globalStatus = "Added ${newEntry.name}"
    }

    fun addTransformerEntryAfterSelection(newEntry: TransformerEntry) {
        addTransformerEntry(selectedIndex, newEntry)
    }

    fun addTransformer(index: Int, definition: TransformerDefinition) {
        addTransformerEntry(index, TransformerEntry(name = definition.label, config = definition.configFactory()))
    }

    fun addTransformerAfterSelection(definition: TransformerDefinition) {
        addTransformer(selectedIndex, definition)
    }

    fun moveTransformer(fromIndex: Int, toIndex: Int) {
        require(fromIndex in transformerList.indices) { "From index out of bounds: $fromIndex" }
        require(toIndex in transformerList.indices) { "To index out of bounds: $toIndex" }
        val newList = if (fromIndex < toIndex) {
            List(transformerList.size) { i ->
                when (i) {
                    in 0..<fromIndex -> transformerList[i]
                    in fromIndex..<toIndex -> transformerList[i + 1]
                    toIndex -> transformerList[fromIndex]
                    else -> transformerList[i]
                }
            }
        } else {
            List(transformerList.size) { i ->
                when (i) {
                    in 0..<toIndex -> transformerList[i]
                    toIndex -> transformerList[fromIndex]
                    in toIndex..<fromIndex -> transformerList[i - 1]
                    else -> transformerList[i]
                }
            }
        }
        transformerProperty = newList
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