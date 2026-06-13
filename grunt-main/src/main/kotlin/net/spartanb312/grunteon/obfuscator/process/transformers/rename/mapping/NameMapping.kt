package net.spartanb312.grunteon.obfuscator.process.transformers.rename.mapping

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import it.unimi.dsi.fastutil.objects.Object2ObjectMap
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.spartanb312.grunteon.obfuscator.process.resource.ResourceOutput
import net.spartanb312.grunteon.obfuscator.util.dot
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Remapper
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.bufferedWriter

class NameMapping : Remapper(Opcodes.ASM9) {

    private val classMappings = Object2ObjectOpenHashMap<String, ClassEntry>()
    private val indyMapping = ConcurrentHashMap<String, String>()
    val revMappings = Object2ObjectOpenHashMap<String, String>()

    fun getMapping(old: String): String? {
        return classMappings.getOrDefault(old, null)?.new
    }

    fun getOriginalClassName(name: String): String {
        return revMappings[name] ?: name
    }

    /**
     * Remaps textual class literals while preserving their original spelling:
     * dotted binary name, slash name, object descriptor, or object array
     * descriptor.
     */
    fun mapClassLiteral(value: String): String? {
        val literal = ClassLiteral.parse(value) ?: return null
        val mapped = getMapping(literal.internalName) ?: return null
        if (mapped == literal.internalName) return null
        return literal.render(mapped)
    }

    /**
     * Reflection member lookups often lack descriptors. Return a new method
     * name only when all overloads with this old name in the owner map to the
     * same target name.
     */
    fun mapUniqueMethodName(owner: String, name: String): String? {
        val oldOwner = getOriginalClassName(owner)
        val entry = classMappings[oldOwner] ?: return null
        return entry.methodMapping.uniqueMappedName(name, ::isMethodDescriptor)
    }

    /**
     * Field descriptors are usually recoverable from mappings, but reflection
     * strings only carry the field name. Keep the same conservative contract as
     * method remapping.
     */
    fun mapUniqueFieldName(owner: String, name: String): String? {
        val oldOwner = getOriginalClassName(owner)
        val entry = classMappings[oldOwner] ?: return null
        return entry.fieldMapping.uniqueMappedName(name, ::isFieldDescriptor)
    }

    fun dump(path: Path) {
        path.bufferedWriter().use { dump(it) }
    }

    fun dump(output: ResourceOutput) {
        OutputStreamWriter(output.openOutputStream()).use { dump(it) }
    }

    fun dump(writer: Writer) {
        val jsonObj = JsonObject().apply {
            classMappings.entries.sortedBy { it.key }.forEach { (prev, entry) ->
                add(prev, JsonObject().apply {
                    addProperty("new", entry.new)
                    add("methods", JsonObject().apply {
                        entry.methodMapping.entries.sortedBy { it.key }.forEach { (k, v) ->
                            addProperty(k, v)
                        }
                    })
                    add("fields", JsonObject().apply {
                        entry.fieldMapping.entries.sortedBy { it.key }.forEach { (k, v) ->
                            addProperty(k, v)
                        }
                    })
                })
            }
        }
        GsonBuilder().setPrettyPrinting().create().toJson(jsonObj, writer)
    }

    fun putIndyMapping(name: String, descriptor: String, newName: String) {
        indyMapping["$name$descriptor"] = newName
    }

    fun putClassMapping(prev: String, new: String) {
        classMappings.computeIfAbsent(prev) { ClassEntry(prev) }.new = new
        revMappings[new] = prev
    }

    fun putMethodMapping(owner: String, name: String, descriptor: String, newName: String) {
        classMappings.computeIfAbsent(owner) { ClassEntry(owner) }.methodMapping["$name$descriptor"] = newName
    }

    fun putFieldMapping(owner: String, name: String, descriptor: String, newName: String) {
        classMappings.computeIfAbsent(owner) { ClassEntry(owner) }.fieldMapping["$name$descriptor"] = newName
    }

    class ClassEntry(val prev: String) {
        var new = prev
        val methodMapping: Object2ObjectMap<String, String> = Object2ObjectMaps.synchronize(Object2ObjectOpenHashMap())
        val fieldMapping: Object2ObjectMap<String, String> = Object2ObjectMaps.synchronize(Object2ObjectOpenHashMap())
    }

    override fun mapMethodName(owner: String?, name: String?, descriptor: String?): String? {
        if (owner == null || name == null || descriptor == null) return name
        return classMappings[owner]?.methodMapping?.get("$name$descriptor") ?: name
    }

    @Deprecated("Deprecated in Java")
    override fun mapInvokeDynamicMethodName(name: String?, descriptor: String?): String? {
        if (name == null || descriptor == null) return name
        return indyMapping["$name$descriptor"] ?: name
    }

    override fun mapBasicInvokeDynamicMethodName(
        name: String?,
        descriptor: String?,
        bootstrapMethodHandle: Handle?,
        vararg bootstrapMethodArguments: Any?
    ): String? {
        if (name == null || descriptor == null) return name
        return indyMapping["$name$descriptor"] ?: name
    }

    override fun mapFieldName(owner: String, name: String, descriptor: String): String {
        return classMappings[owner]?.fieldMapping?.get("$name$descriptor") ?: name
    }

    override fun map(key: String?): String? {
        if (key == null) return null
        return classMappings[key]?.new ?: key
    }

    private fun Object2ObjectMap<String, String>.uniqueMappedName(
        name: String,
        isDescriptor: (String) -> Boolean
    ): String? {
        var found = false
        var mappedName: String? = null
        synchronized(this) {
            object2ObjectEntrySet().forEach { entry ->
                val key = entry.key
                if (!key.startsWith(name)) return@forEach
                val descriptor = key.substring(name.length)
                if (!isDescriptor(descriptor)) return@forEach
                if (!found) {
                    found = true
                    mappedName = entry.value
                } else if (mappedName != entry.value) {
                    return null
                }
            }
        }
        return if (found) mappedName else null
    }

    private sealed class ClassLiteral(val internalName: String) {
        abstract fun render(mappedInternalName: String): String

        class BinaryName(internalName: String, private val dotted: Boolean) : ClassLiteral(internalName) {
            override fun render(mappedInternalName: String): String {
                return if (dotted) mappedInternalName.dot else mappedInternalName
            }
        }

        class Descriptor(
            internalName: String,
            private val dimensions: Int,
            private val dotted: Boolean,
            private val fullDescriptor: Boolean
        ) : ClassLiteral(internalName) {
            override fun render(mappedInternalName: String): String {
                val mapped = if (dotted) mappedInternalName.dot else mappedInternalName
                return if (fullDescriptor) {
                    "L$mapped;"
                } else {
                    buildString {
                        repeat(dimensions) { append('[') }
                        append('L')
                        append(mapped)
                        append(';')
                    }
                }
            }
        }

        companion object {
            fun parse(value: String): ClassLiteral? {
                if (value.isEmpty()) return null
                if (value[0] == '[') {
                    val normalized = value.replace('.', '/')
                    val dimensions = normalized.indexOfFirst { it != '[' }
                    if (dimensions <= 0 || normalized.getOrNull(dimensions) != 'L') return null
                    val end = normalized.indexOf(';', dimensions + 1)
                    if (end != normalized.lastIndex) return null
                    val internalName = normalized.substring(dimensions + 1, end)
                    return Descriptor(internalName, dimensions, value.contains('.'), false)
                }
                if (value.length > 2 && value[0] == 'L' && value.last() == ';') {
                    val internalName = value.substring(1, value.lastIndex).replace('.', '/')
                    return Descriptor(internalName, 0, value.contains('.'), true)
                }
                return BinaryName(value.replace('.', '/'), value.contains('.'))
            }
        }
    }

    private companion object {
        fun isMethodDescriptor(desc: String): Boolean {
            if (!desc.startsWith("(")) return false
            return try {
                Type.getMethodType(desc).descriptor == desc
            } catch (_: Throwable) {
                false
            }
        }

        fun isFieldDescriptor(desc: String): Boolean {
            if (desc.isEmpty() || desc[0] == '(' || desc == "V") return false
            return parseFieldDescriptor(desc, 0) == desc.length
        }

        private fun parseFieldDescriptor(desc: String, start: Int): Int {
            var index = start
            while (index < desc.length && desc[index] == '[') index++
            if (index >= desc.length) return -1
            return when (desc[index]) {
                'Z', 'C', 'B', 'S', 'I', 'F', 'J', 'D' -> index + 1
                'L' -> {
                    val end = desc.indexOf(';', index + 1)
                    if (end == -1) -1 else end + 1
                }

                else -> -1
            }
        }
    }

}
