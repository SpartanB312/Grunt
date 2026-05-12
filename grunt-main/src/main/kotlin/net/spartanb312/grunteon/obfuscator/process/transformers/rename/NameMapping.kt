package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import it.unimi.dsi.fastutil.objects.Object2ObjectMap
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.spartanb312.grunteon.obfuscator.process.resource.ResourceOutput
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
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

}
