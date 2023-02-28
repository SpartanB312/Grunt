package net.spartanb312.grunt.process.resource

import net.spartanb312.grunt.config.Configs
import java.util.concurrent.atomic.AtomicInteger

sealed class NameGenerator(val name: String) {

    abstract val chars: List<Char>
    private val size get() = chars.size
    private val index = AtomicInteger(Configs.Settings.dictionaryStartIndex)
    private val methodOverloads = hashMapOf<String, MutableList<String>>() // Name Descs

    var overloadsCount = 0; private set
    var actualNameCount = 0; private set

    fun nextName(): String {
        var index = index.getAndIncrement()
        return if (index == 0) chars[0].toString()
        else {
            val charArray = mutableListOf<Char>()
            while (true) {
                charArray.add(chars[index % size])
                index /= size
                if (index == 0) break
            }
            charArray.reversed().joinToString(separator = "")
        }
    }

    @Synchronized
    fun nextName(overload: Boolean, desc: String): String {
        if (!overload) return nextName()
        else {
            //nameCache[desc]?.let { return it }
            for (pair in methodOverloads) {
                if (!pair.value.contains(desc)) {
                    pair.value.add(desc)
                    overloadsCount++
                    return pair.key
                }
            }
            // Generate a new one
            val newName = nextName()
            methodOverloads[newName] = mutableListOf(desc)
            actualNameCount++
            return newName
        }
    }

    class Alphabet : NameGenerator("alphabet") {
        override val chars = ('a'..'z') + ('A'..'Z')
    }

    class Chinese : NameGenerator("chinese") {
        override val chars = listOf('操', '你', '妈', '傻', '逼', '滚', '笨', '猪')
    }

    class Confuse : NameGenerator("confuse") {
        override val chars = listOf('i', 'I', 'l', '1')
    }

    class Custom : NameGenerator("custom") {
        override val chars = kotlin.run {
            val charList = mutableListOf<Char>()
            Configs.Settings.customDictionary.forEach {
                if (it.isNotEmpty()) charList.add(it[0])
            }
            charList.toSet().toList().ifEmpty { ('a'..'z') + ('A'..'Z') }
        }
    }

    companion object {
        fun getByName(name: String): NameGenerator =
            when (name.lowercase()) {
                "alphabet" -> Alphabet()
                "chinese" -> Chinese()
                "confuse" -> Confuse()
                "custom" -> Custom()
                else -> Alphabet()
            }
    }

}