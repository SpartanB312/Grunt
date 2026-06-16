package net.spartanb312.grunteon.obfuscator.process.nativecode

import net.spartanb312.grunteon.obfuscator.util.NATIVE_EXCLUDED
import net.spartanb312.grunteon.obfuscator.util.NATIVE_INCLUDED
import net.spartanb312.grunteon.obfuscator.util.NATIVE_JVM_BRIDGE
import net.spartanb312.grunteon.obfuscator.util.extensions.removeAnnotations
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

internal fun normalizeNativeAnnotation(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    val internal = if (trimmed.startsWith("L") && trimmed.endsWith(";")) {
        trimmed.substring(1, trimmed.length - 1)
    } else {
        trimmed
    }.replace('.', '/')
    return "L$internal;"
}

internal fun normalizedAnnotationSet(values: Iterable<String>): Set<String> {
    return values
        .map(::normalizeNativeAnnotation)
        .filter { it.isNotEmpty() }
        .toSet()
}

internal fun ClassNode.annotationDescs(): Set<String> {
    return (visibleAnnotations.orEmpty().asSequence() + invisibleAnnotations.orEmpty().asSequence())
        .map(AnnotationNode::desc)
        .map(::normalizeNativeAnnotation)
        .toSet()
}

internal fun MethodNode.annotationDescs(): Set<String> {
    return (visibleAnnotations.orEmpty().asSequence() + invisibleAnnotations.orEmpty().asSequence())
        .map(AnnotationNode::desc)
        .map(::normalizeNativeAnnotation)
        .toSet()
}

internal object NativeAnnotationCleaner {
    private val builtinNativeAnnotations = setOf(
        normalizeNativeAnnotation(NATIVE_INCLUDED),
        normalizeNativeAnnotation(NATIVE_EXCLUDED),
        normalizeNativeAnnotation(NATIVE_JVM_BRIDGE)
    )

    fun clean(classNode: ClassNode) {
        classNode.removeAnnotations(builtinNativeAnnotations)
        classNode.methods.forEach { it.removeAnnotations(builtinNativeAnnotations) }
    }
}
