package net.spartanb312.grunt.config

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class Configurable(var name: String) {

    private val values = mutableListOf<AbstractValue<*>>()

    fun getValues() = values.toList()

    fun saveValue(): JsonObject {
        return JsonObject().apply {
            values.forEach { it.saveValue(this) }
        }
    }

    fun getValue(jsonObject: JsonObject) {
        values.forEach { it.getValue(jsonObject) }
    }

    fun <T> value0(value: AbstractValue<T>): AbstractValue<T> {
        values.add(value)
        return value
    }

    fun resetAll() = values.forEach { it.reset() }

}

fun Configurable.setting(name: String, value: File) = value0(FileValue(name, value))
fun Configurable.setting(name: String, value: String) = value0(StringValue(name, value))
fun Configurable.setting(name: String, value: Int) = value0(IntValue(name, value))
fun Configurable.setting(name: String, value: Float) = value0(FloatValue(name, value))
fun Configurable.setting(name: String, value: Boolean) = value0(BooleanValue(name, value))
fun Configurable.setting(name: String, value: List<String>) = value0(ListValue(name, value))

abstract class AbstractValue<T>(val name: String, val defaultValue: T) : ReadWriteProperty<Any?, T> {

    private val valueListeners = ArrayList<(prev: T, input: T) -> Unit>()
    var value = defaultValue
        set(value) {
            if (value != field) {
                val prev = field
                val new = value
                field = new
                valueListeners.forEach { it(prev, field) }
            }
        }

    abstract fun saveValue(jsonObject: JsonObject)
    abstract fun getValue(jsonObject: JsonObject)

    final override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
    final override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    fun listen(listener: (prev: T, input: T) -> Unit): AbstractValue<T> {
        this.valueListeners.add(listener)
        return this
    }

    fun reset() {
        value = defaultValue
    }

}

class FileValue(name: String, value: File) : AbstractValue<String>(name, value.name) {
    override fun saveValue(jsonObject: JsonObject) = jsonObject.addProperty(name, value)
    override fun getValue(jsonObject: JsonObject) {
        value = jsonObject[name]?.asString ?: value
    }
}

class StringValue(name: String, value: String) : AbstractValue<String>(name, value) {
    override fun saveValue(jsonObject: JsonObject) = jsonObject.addProperty(name, value)
    override fun getValue(jsonObject: JsonObject) {
        value = jsonObject[name]?.asString ?: value
    }
}

class IntValue(name: String, value: Int) : AbstractValue<Int>(name, value) {
    override fun saveValue(jsonObject: JsonObject) = jsonObject.addProperty(name, value)
    override fun getValue(jsonObject: JsonObject) {
        value = jsonObject[name]?.asInt ?: value
    }
}

class FloatValue(name: String, value: Float) : AbstractValue<Float>(name, value) {
    override fun saveValue(jsonObject: JsonObject) = jsonObject.addProperty(name, value)
    override fun getValue(jsonObject: JsonObject) {
        value = jsonObject[name]?.asFloat ?: value
    }
}

class BooleanValue(name: String, value: Boolean) : AbstractValue<Boolean>(name, value) {
    override fun saveValue(jsonObject: JsonObject) = jsonObject.addProperty(name, value)
    override fun getValue(jsonObject: JsonObject) {
        value = jsonObject[name]?.asBoolean ?: value
    }
}

class ListValue(name: String, value: List<String>) : AbstractValue<List<String>>(name, value) {
    override fun saveValue(jsonObject: JsonObject) = jsonObject.add(name, JsonArray().apply {
        value.forEach { add(it) }
    })

    override fun getValue(jsonObject: JsonObject) {
        value = jsonObject[name]?.asJsonArray?.map { it.asString } ?: value
    }
}