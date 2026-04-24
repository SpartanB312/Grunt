package net.spartanb312.grunteon.index.io

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import net.spartanb312.grunteon.index.FILE_VERSION
import net.spartanb312.grunteon.index.info.ClassInfo
import net.spartanb312.grunteon.index.info.FieldInfo
import net.spartanb312.grunteon.index.info.MethodInfo
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

private val File.jsonMap: Map<String, JsonElement>
    get() {
        val loadJson = BufferedReader(FileReader(this))
        val map = mutableMapOf<String, JsonElement>()
        JsonParser.parseReader(loadJson).asJsonObject.entrySet().forEach {
            map[it.key] = it.value
        }
        loadJson.close()
        return map
    }

fun readFromFile(file: File, version: String = FILE_VERSION): List<ClassInfo> {
    val map = file.jsonMap
    val versionRead = map["version"]!!.asString
    val v1 = version.split(".").map { it.toInt() }.toIntArray()
    val v2 = versionRead.split(".").map { it.toInt() }.toIntArray()
    if (v2.isLessThan(v1)) throw Exception("Outdated version $versionRead, expect $version")
    return map["classes"]!!.asJsonArray.map { clazz ->
        val classObj = clazz.asJsonObject
        val classInfo = ClassInfo(
            classObj["access"]!!.asInt,
            classObj["name"]!!.asString,
            classObj["signature"]?.asString,
            classObj["superName"]?.asString,
            classObj["interfaces"]?.asJsonArray?.map { it.asString }
        )
        classInfo.methods = classObj["methods"].asJsonArray.map { method ->
            val methodObj = method.asJsonObject
            MethodInfo(
                methodObj["access"]!!.asInt,
                methodObj["name"]!!.asString,
                methodObj["desc"]!!.asString,
                methodObj["signature"]?.asString,
            )
        }
        classInfo.fields = classObj["fields"].asJsonArray.map { field ->
            val fieldObj = field.asJsonObject
            FieldInfo(
                fieldObj["access"]!!.asInt,
                fieldObj["name"]!!.asString,
                fieldObj["desc"]!!.asString,
                fieldObj["signature"]?.asString,
            )
        }
        classInfo
    }
}
