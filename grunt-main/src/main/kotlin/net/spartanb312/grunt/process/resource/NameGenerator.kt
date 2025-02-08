package net.spartanb312.grunt.process.resource

import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.utils.logging.Logger
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

sealed class NameGenerator(val name: String) {

    abstract val elements: List<String>
    private val size get() = elements.size
    private val index = AtomicInteger(Configs.Settings.dictionaryStartIndex)
    private val methodOverloads = hashMapOf<String, MutableList<String>>() // Name Descs

    var overloadsCount = 0; private set
    var actualNameCount = 0; private set

    fun nextName(): String {
        var index = index.getAndIncrement()
        return if (index == 0) elements[0]
        else {
            val charArray = mutableListOf<String>()
            while (true) {
                charArray.add(elements[index % size])
                index /= size
                if (index == 0) break
                index -= 1
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
        override val elements = (('a'..'z') + ('A'..'Z')).map { it.toString() }
    }

    class Numbers : NameGenerator("numbers") {
        override val elements = ('0'..'9').map { it.toString() }
    }

    class ConfuseIL : NameGenerator("confuseIL") {
        override val elements = listOf("I", "i", "l", "1")
    }

    class Confuse0O : NameGenerator("confuse0O") {
        override val elements = listOf("O", "o", "0")
    }

    class ConfuseS5 : NameGenerator("confuseS5") {
        override val elements = listOf("S", "s", "5", "$")
    }

    class Arabic : NameGenerator("Arabic") {
        override val elements = ('\u0600'..'\u06ff').asSequence().map { it.toString() }.toList()
    }

    class Custom : NameGenerator("custom") {
        override val elements: List<String> = kotlin.run {
            val file = File(Configs.Settings.customDictionary)
            if (!file.exists()) {
                // Dictionary file does not exist, use default dictionary
                Logger.error("Could not find custom dictionary ${file.name}")
                Logger.error("Using default fallback dictionary!")
                return@run Alphabet().elements
            }
            Files.readAllLines(file.toPath())
        }
    }

    companion object {
        fun getByName(name: String): NameGenerator =
            when (name.lowercase()) {
                "default", "alphabet" -> Alphabet()
                "numbers" -> Numbers()
                "confuseil" -> ConfuseIL()
                "confuse0o" -> Confuse0O()
                "confuses5" -> ConfuseS5()
                "arabic" -> Arabic()
                "custom" -> Custom()
                else -> Alphabet()
            }
    }

}
