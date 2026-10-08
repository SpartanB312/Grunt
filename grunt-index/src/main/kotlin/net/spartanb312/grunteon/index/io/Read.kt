package net.spartanb312.grunteon.index.io

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import net.spartanb312.grunteon.index.FILE_VERSION
import net.spartanb312.grunteon.index.info.ClassInfo
import net.spartanb312.grunteon.index.info.FieldInfo
import net.spartanb312.grunteon.index.info.MethodInfo
import java.io.File
import java.math.BigDecimal

fun readFromFile(file: File, version: String = FILE_VERSION): List<ClassInfo> {
    return file.bufferedReader(Charsets.UTF_8).use { input ->
        JsonReader(input).use { reader ->
            // JsonParser.parseReader previously accepted Gson's lenient JSON syntax.
            reader.strictness = Strictness.LENIENT
            var versionRead: String? = null
            var classes: List<ClassInfo>? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "version" -> versionRead = reader.readTextValue()
                    "classes" -> classes = reader.readList { readClass() }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            check(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing content after index" }
            val actualVersion = requireNotNull(versionRead) { "Missing index version" }
            val v1 = version.split(".").map { it.toInt() }.toIntArray()
            val v2 = actualVersion.split(".").map { it.toInt() }.toIntArray()
            if (v2.isLessThan(v1)) throw Exception("Outdated version $actualVersion, expect $version")
            requireNotNull(classes) { "Missing index classes" }
        }
    }
}

private fun JsonReader.readClass(): ClassInfo {
    var access: Int? = null
    var name: String? = null
    var signature: String? = null
    var superName: String? = null
    var interfaces: List<String>? = null
    var methods: List<MethodInfo>? = null
    var fields: List<FieldInfo>? = null
    beginObject()
    while (hasNext()) {
        when (nextName()) {
            "access" -> access = readAccess()
            "name" -> name = readTextValue()
            "signature" -> signature = readTextValue()
            "superName" -> superName = readTextValue()
            "interfaces" -> interfaces = readList { readTextValue() }
            "methods" -> methods = readList { readMember(::MethodInfo) }
            "fields" -> fields = readList { readMember(::FieldInfo) }
            else -> skipValue()
        }
    }
    endObject()
    return ClassInfo(requireNotNull(access), requireNotNull(name), signature, superName, interfaces).apply {
        this.methods = requireNotNull(methods) { "Missing methods in $name" }
        this.fields = requireNotNull(fields) { "Missing fields in $name" }
    }
}

private fun <T> JsonReader.readMember(factory: (Int, String, String, String?) -> T): T {
    var access: Int? = null
    var name: String? = null
    var desc: String? = null
    var signature: String? = null
    beginObject()
    while (hasNext()) {
        when (nextName()) {
            "access" -> access = readAccess()
            "name" -> name = readTextValue()
            "desc" -> desc = readTextValue()
            "signature" -> signature = readTextValue()
            else -> skipValue()
        }
    }
    endObject()
    return factory(requireNotNull(access), requireNotNull(name), requireNotNull(desc), signature)
}

private inline fun <T> JsonReader.readList(readElement: JsonReader.() -> T): List<T> {
    val values = ArrayList<T>()
    beginArray()
    while (hasNext()) values += readElement()
    endArray()
    return values
}

private fun JsonReader.readTextValue(): String {
    return if (peek() == JsonToken.BOOLEAN) nextBoolean().toString() else nextString()
}

private fun JsonReader.readAccess(): Int {
    // JsonPrimitive.asInt coerced numeric literals via Number.intValue, but parsed strings as integers.
    if (peek() != JsonToken.NUMBER) return readTextValue().toInt()
    val number = nextString()
    return number.toIntOrNull() ?: number.toLongOrNull()?.toInt() ?: BigDecimal(number).toInt()
}
