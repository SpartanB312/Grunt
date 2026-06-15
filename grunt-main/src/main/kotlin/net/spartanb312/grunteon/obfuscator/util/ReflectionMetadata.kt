package net.spartanb312.grunteon.obfuscator.util

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import net.spartanb312.grunteon.obfuscator.util.extensions.findAnnotation
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.MethodNode
import java.nio.charset.StandardCharsets
import java.util.Base64

private const val REFLECTION_METADATA_VALUE = "value"
private const val REFLECTION_METADATA_SEPARATOR = "|"
private const val REFLECTION_METADATA_OWNER_SEPARATOR = ","

enum class ReflectionMetadataKind(val id: String) {
    ClassLiteral("C"),
    Method("M"),
    Field("F");

    companion object {
        fun fromId(id: String): ReflectionMetadataKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Structured reflection remap hint stored on a method as a temporary
 * annotation entry.
 *
 * The annotation still stores a plain `String[]` so PostProcess can treat it
 * like other draft metadata and no runtime annotation class is required. Values
 * are URL-safe base64 encoded to avoid separator collisions with obfuscated
 * names or user strings.
 */
data class ReflectionMetadataEntry(
    val kind: ReflectionMetadataKind,
    val owners: List<String>,
    val value: String
) {
    fun encode(): String {
        val encodedOwners = owners.joinToString(REFLECTION_METADATA_OWNER_SEPARATOR) { it.encodeBase64() }
        return listOf(kind.id, encodedOwners, value.encodeBase64()).joinToString(REFLECTION_METADATA_SEPARATOR)
    }

    companion object {
        fun decode(value: String): ReflectionMetadataEntry? {
            val parts = value.split(REFLECTION_METADATA_SEPARATOR)
            if (parts.size != 3) return null
            val kind = ReflectionMetadataKind.fromId(parts[0]) ?: return null
            val owners = if (parts[1].isEmpty()) {
                emptyList()
            } else {
                parts[1].split(REFLECTION_METADATA_OWNER_SEPARATOR).mapNotNull { it.decodeBase64OrNull() }
            }
            val literal = parts[2].decodeBase64OrNull() ?: return null
            return ReflectionMetadataEntry(kind, owners, literal)
        }
    }
}

/**
 * Adds structured reflection metadata to a method. This is paired with
 * StringBlacklist, but intentionally separate: encryption only needs string
 * membership, while remapping needs owner and literal kind information.
 */
fun MethodNode.appendReflectionMetadata(entries: Iterable<ReflectionMetadataEntry>) {
    val annotation = findAnnotation(REFLECTION_METADATA) ?: AnnotationNode(REFLECTION_METADATA).also { node ->
        invisibleAnnotations = invisibleAnnotations ?: mutableListOf()
        invisibleAnnotations.add(node)
    }
    val values = annotation.values ?: mutableListOf<Any>().also { annotation.values = it }
    val array = values.arrayValue(REFLECTION_METADATA_VALUE) ?: mutableListOf<Any>().also {
        values.add(REFLECTION_METADATA_VALUE)
        values.add(it)
    }
    val existed = ObjectOpenHashSet<String>(array.size)
    array.forEach { value ->
        if (value is String) existed.add(value)
    }
    entries.forEach { entry ->
        val encoded = entry.encode()
        if (existed.add(encoded)) array.add(encoded)
    }
}

/**
 * Reads best-effort metadata. Malformed entries are ignored so a damaged draft
 * annotation cannot break output generation.
 */
fun MethodNode.reflectionMetadata(): List<ReflectionMetadataEntry> {
    val annotation = findAnnotation(REFLECTION_METADATA) ?: return emptyList()
    val values = annotation.values ?: return emptyList()
    val array = values.arrayValue(REFLECTION_METADATA_VALUE) ?: return emptyList()
    return array.mapNotNull { value ->
        if (value is String) ReflectionMetadataEntry.decode(value) else null
    }
}

private fun String.encodeBase64(): String {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray(StandardCharsets.UTF_8))
}

private fun String.decodeBase64OrNull(): String? {
    return try {
        String(Base64.getUrlDecoder().decode(this), StandardCharsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }
}

@Suppress("UNCHECKED_CAST")
private fun MutableList<Any>.arrayValue(name: String): MutableList<Any>? {
    var index = 0
    while (index + 1 < size) {
        if (this[index] == name) return this[index + 1] as? MutableList<Any>
        index += 2
    }
    return null
}
