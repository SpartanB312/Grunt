package net.spartanb312.grunt.dictionary

import net.spartanb312.grunt.config.Configs
import java.util.concurrent.atomic.AtomicInteger

sealed class NameGenerator {

    abstract val chars: List<Char>
    private val size get() = chars.size
    private val index = AtomicInteger(Configs.Settings.dictionaryStartIndex)

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

    class Alphabet : NameGenerator() {
        override val chars = ('a'..'z') + ('A'..'Z')
    }

    class Chinese : NameGenerator() {
        override val chars = listOf('操', '你', '妈', '傻', '逼', '滚', '笨', '猪')
    }

    class Confuse : NameGenerator() {
        override val chars = listOf('i', 'I', 'l', '1')
    }

    class Custom : NameGenerator() {
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