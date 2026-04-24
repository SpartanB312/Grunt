package net.spartanb312.grunteon.index.io

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.spartanb312.grunteon.index.info.ClassInfo
import net.spartanb312.grunteon.index.io.toJsonObj
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

private val gsonPretty: Gson = GsonBuilder().setPrettyPrinting().create()

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

private fun JsonObject.saveToFile(file: File) {
    if (!file.exists()) {
        file.parentFile?.mkdirs()
        file.createNewFile()
    }
    val saveJSon = PrintWriter(FileWriter(file))
    saveJSon.println(gsonPretty.toJson(this))
    saveJSon.close()
}

fun Collection<ClassInfo>.saveToFile(file: File) {
    val obj = JsonObject()
    obj.add("classes", JsonArray().apply { this@saveToFile.forEach { add(it.toJsonObj()) } })
    obj.saveToFile(file)
}