package net.spartanb312.grunt.obfuscate

import java.util.concurrent.atomic.AtomicInteger

class NameGenerator {

    private val chars = ('a'..'z') + ('A'..'Z') // 52 chars
    private val index = AtomicInteger(0)

    fun nextName(): String {
        var index = index.getAndIncrement()
        return if (index == 0) chars[0].toString()
        else {
            val charArray = mutableListOf<Char>()
            while (true) {
                charArray.add(chars[index % 52])
                index /= 52
                if (index == 0) break
            }
            charArray.reversed().joinToString(separator = "")
        }
    }

}