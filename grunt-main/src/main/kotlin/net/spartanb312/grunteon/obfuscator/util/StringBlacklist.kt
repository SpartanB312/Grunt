package net.spartanb312.grunteon.obfuscator.util

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import net.spartanb312.grunteon.obfuscator.util.extensions.findAnnotation
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.MethodNode

private const val STRING_BLACKLIST_VALUE = "value"

/**
 * Marks method-local string literals that must remain plaintext.
 *
 * ReflectionSupport uses this as a narrow string-encryption blacklist; it does
 * not carry owner information. Owner and kind data live in ReflectionMetadata.
 */
fun MethodNode.appendStringBlacklist(strings: Iterable<String>) {
    val annotation = findAnnotation(STRING_BLACKLIST) ?: AnnotationNode(STRING_BLACKLIST).also { node ->
        invisibleAnnotations = invisibleAnnotations ?: mutableListOf()
        invisibleAnnotations.add(node)
    }
    val values = annotation.values ?: mutableListOf<Any>().also { annotation.values = it }
    val array = values.arrayValue(STRING_BLACKLIST_VALUE) ?: mutableListOf<Any>().also {
        values.add(STRING_BLACKLIST_VALUE)
        values.add(it)
    }
    val existed = ObjectOpenHashSet<String>(array.size)
    array.forEach { value ->
        if (value is String) existed.add(value)
    }
    strings.forEach { value ->
        if (value.isNotEmpty() && existed.add(value)) array.add(value)
    }
}

/**
 * Returns the method-local plaintext string blacklist. Missing annotations are
 * treated as an empty set so transformers can query it on hot paths.
 */
fun MethodNode.stringBlacklist(): ObjectOpenHashSet<String> {
    val annotation = findAnnotation(STRING_BLACKLIST) ?: return ObjectOpenHashSet()
    val values = annotation.values ?: return ObjectOpenHashSet()
    val array = values.arrayValue(STRING_BLACKLIST_VALUE) ?: return ObjectOpenHashSet()
    val result = ObjectOpenHashSet<String>(array.size)
    array.forEach { value ->
        if (value is String) result.add(value)
    }
    return result
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
