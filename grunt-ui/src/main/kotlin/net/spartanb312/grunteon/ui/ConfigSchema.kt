package net.spartanb312.grunteon.ui

import kotlinx.serialization.Transient
import net.spartanb312.grunteon.obfuscator.process.HiddenFromAutoParameter
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.isAccessible

/** Schema only: never retain the current editor value or an onChange callback. */
internal class ConfigEditorSchema(clazz: KClass<*>) {
    val copy = checkNotNull(clazz.memberFunctions.find { it.name == "copy" }) { "$clazz is not a data class" }
    val parameters = copy.parameters.drop(1).associateBy { it.name!! }
    @Suppress("UNCHECKED_CAST")
    val properties = clazz.memberProperties
        .filter { it.javaField != null }
        .filter { it.annotations.none { annotation -> annotation is HiddenFromAutoParameter || annotation is Transient } }
        .sortedBy { parameters[it.name]?.index ?: Int.MAX_VALUE } as List<KProperty1<Any, *>>
}

internal object ConfigSchemas {
    private val schemas = ConcurrentHashMap<KClass<*>, ConfigEditorSchema>()
    fun editor(clazz: KClass<*>): ConfigEditorSchema = schemas.computeIfAbsent(clazz, ::ConfigEditorSchema)
}

private class SnapshotSchema(clazz: KClass<*>) {
    val constructor: KFunction<*> = checkNotNull(clazz.primaryConstructor).also { it.isAccessible = true }
    private val properties = clazz.memberProperties.associateBy { it.name }
    val arguments = constructor.parameters.map { parameter ->
        parameter to checkNotNull(properties[parameter.name]).also { it.isAccessible = true }
    }
    // Serializable properties can also live in the class body, outside data-class copy/constructor parameters.
    val bodyFields = properties.values.filter { it.name !in constructor.parameters.map { parameter -> parameter.name } }
        .mapNotNull { it.javaField }
        .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
        .onEach { it.isAccessible = true }
}

private val snapshotSchemas = ConcurrentHashMap<KClass<*>, SnapshotSchema>()

/** Detach collection backing stores on the UI thread before an IO task can observe the value.
 * The auto-editor uses copy-on-write data classes; scalar/opaque plugin values are never edited in place.
 */
@Suppress("UNCHECKED_CAST")
internal fun <T> configSnapshot(value: T): T = when (value) {
    is String, is Number, is Boolean, is Char, is Enum<*>, is java.nio.file.Path -> value
    is ByteArray -> value.copyOf()
    is IntArray -> value.copyOf()
    is LongArray -> value.copyOf()
    is CharArray -> value.copyOf()
    is ShortArray -> value.copyOf()
    is BooleanArray -> value.copyOf()
    is FloatArray -> value.copyOf()
    is DoubleArray -> value.copyOf()
    is Array<*> -> (value.copyOf() as Array<Any?>).also { copy ->
        copy.indices.forEach { copy[it] = configSnapshot(copy[it]) }
    }
    is List<*> -> value.map { configSnapshot(it) }
    is Set<*> -> value.mapTo(linkedSetOf()) { configSnapshot(it) }
    is Map<*, *> -> value.entries.associate { configSnapshot(it.key) to configSnapshot(it.value) }
    null -> null
    else -> if (value::class.isData) {
        val schema = snapshotSchemas.computeIfAbsent(value::class, ::SnapshotSchema)
        schema.constructor.callBy(schema.arguments.associate { (parameter, property) ->
            parameter to configSnapshot(property.getter.call(value))
        }).also { copy ->
            schema.bodyFields.forEach { field -> field.set(copy, configSnapshot(field.get(value))) }
        }
    } else value
} as T

/** Compare live UI-owned content with a detached snapshot, not data-class equals (which ignores body fields
 * and compares arrays by identity). Collection iteration order is retained because it also affects JSON.
 */
internal fun configContentEquals(current: Any?, snapshot: Any?): Boolean {
    if (current === snapshot) return true
    if (current == null || snapshot == null) return false
    return when (current) {
        is ByteArray -> snapshot is ByteArray && current.contentEquals(snapshot)
        is IntArray -> snapshot is IntArray && current.contentEquals(snapshot)
        is LongArray -> snapshot is LongArray && current.contentEquals(snapshot)
        is CharArray -> snapshot is CharArray && current.contentEquals(snapshot)
        is ShortArray -> snapshot is ShortArray && current.contentEquals(snapshot)
        is BooleanArray -> snapshot is BooleanArray && current.contentEquals(snapshot)
        is FloatArray -> snapshot is FloatArray && current.contentEquals(snapshot)
        is DoubleArray -> snapshot is DoubleArray && current.contentEquals(snapshot)
        is Array<*> -> snapshot is Array<*> && current.size == snapshot.size &&
            current.indices.all { configContentEquals(current[it], snapshot[it]) }
        is List<*> -> snapshot is List<*> && current.size == snapshot.size &&
            sameConfigElements(current, snapshot)
        is Set<*> -> snapshot is Set<*> && current.size == snapshot.size &&
            sameConfigElements(current, snapshot)
        is Map<*, *> -> {
            if (snapshot !is Map<*, *> || current.size != snapshot.size) false else {
                val expected = snapshot.entries.iterator()
                current.entries.all { entry ->
                    val other = expected.next()
                    configContentEquals(entry.key, other.key) && configContentEquals(entry.value, other.value)
                }
            }
        }
        is String, is Number, is Boolean, is Char, is Enum<*>, is java.nio.file.Path -> current == snapshot
        else -> {
            if (current::class != snapshot::class) false
            else if (current::class.isData) {
                val schema = snapshotSchemas.computeIfAbsent(current::class, ::SnapshotSchema)
                schema.arguments.all { (_, property) ->
                    configContentEquals(property.getter.call(current), property.getter.call(snapshot))
                } && schema.bodyFields.all { field ->
                    configContentEquals(field.get(current), field.get(snapshot))
                }
            } else current == snapshot // Opaque plugin values are read-only to the auto-editor.
        }
    }
}

private fun sameConfigElements(current: Iterable<*>, snapshot: Iterable<*>): Boolean {
    val expected = snapshot.iterator()
    return current.all { expected.hasNext() && configContentEquals(it, expected.next()) } && !expected.hasNext()
}
