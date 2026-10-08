package net.spartanb312.grunteon.index.io

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.stream.JsonWriter
import net.spartanb312.grunteon.index.FILE_VERSION
import net.spartanb312.grunteon.index.info.ClassInfo
import java.io.File

fun ClassInfo.toJsonObj(): JsonObject {
    return JsonObject().apply {
        addProperty("access", access)
        addProperty("name", name)
        if (superName != null) addProperty("superName", superName)
        if (!interfaces.isNullOrEmpty()) add(
            "interfaces",
            JsonArray().apply { interfaces.forEach { add(it) } }
        )
        add("methods", JsonArray().apply {
            methods.forEach {
                add(JsonObject().apply {
                    addProperty("access", it.access)
                    addProperty("name", it.name)
                    addProperty("desc", it.desc)
                    if (it.signature != null) addProperty("signature", it.signature)
                })
            }
        })
        add("fields", JsonArray().apply {
            fields.forEach {
                add(JsonObject().apply {
                    addProperty("access", it.access)
                    addProperty("name", it.name)
                    addProperty("desc", it.desc)
                    if (it.signature != null) addProperty("signature", it.signature)
                })
            }
        })
    }
}

fun Collection<ClassInfo>.saveToFile(file: File) {
    if (!file.exists()) file.parentFile?.mkdirs()
    file.bufferedWriter(Charsets.UTF_8).use { output ->
        val writer = JsonWriter(output).apply {
            setIndent("  ")
            isHtmlSafe = true // Match Gson's existing pretty-printing and escaping.
        }
        writer.beginObject()
        writer.name("version").value(FILE_VERSION)
        writer.name("classes").beginArray()
        for (clazz in this) writer.writeClass(clazz)
        writer.endArray()
        writer.endObject()
        writer.flush()
        output.append(System.lineSeparator())
    }
}

private fun JsonWriter.writeClass(clazz: ClassInfo) {
    beginObject()
    name("access").value(clazz.access)
    name("name").value(clazz.name)
    // Class signatures were not emitted by toJsonObj(); retain that on-disk format contract.
    if (clazz.superName != null) name("superName").value(clazz.superName)
    if (!clazz.interfaces.isNullOrEmpty()) {
        name("interfaces").beginArray()
        clazz.interfaces.forEach { value(it) }
        endArray()
    }
    name("methods").beginArray()
    for (method in clazz.methods) {
        beginObject()
        name("access").value(method.access)
        name("name").value(method.name)
        name("desc").value(method.desc)
        if (method.signature != null) name("signature").value(method.signature)
        endObject()
    }
    endArray()
    name("fields").beginArray()
    for (field in clazz.fields) {
        beginObject()
        name("access").value(field.access)
        name("name").value(field.name)
        name("desc").value(field.desc)
        if (field.signature != null) name("signature").value(field.signature)
        endObject()
    }
    endArray()
    endObject()
}