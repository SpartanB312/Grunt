package net.spartanb312.grunt.process.resource

import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.utils.blanks
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

    class Blank : NameGenerator("Blank") {
        override val elements = blanks.map { it.toString() }
    }

    class Alphabet : NameGenerator("alphabet") {
        override val elements = (('a'..'z') + ('A'..'Z')).map { it.toString() }
    }

    class Chinese : NameGenerator("chinese") {
        override val elements = listOf("操", "你", "妈", "傻", "逼", "滚", "绷", "赢", "麻", "孝", "典", "唐", "寄")
    }

    class Japanese : NameGenerator("japanese") {
        override val elements = listOf(
            "あ", "ア", "い", "イ", "う", "ウ", "え", "エ", "お", "オ",
            "か", "カ", "き", "キ", "く", "ク", "け", "ケ", "こ", "コ",
            "さ", "サ", "し", "シ", "す", "ス", "せ", "セ", "そ", "ソ",
            "た", "タ", "ち", "チ", "つ", "ツ", "て", "テ", "と", "ト",
            "な", "ナ", "に", "ニ", "ぬ", "ヌ", "ね", "ネ", "の", "ノ",
            "は", "ハ", "ひ", "ヒ", "ふ", "フ", "へ", "ヘ", "ほ", "ホ",
            "ま", "マ", "み", "ミ", "む", "ム", "め", "メ", "も", "モ",
            "や", "ヤ", "ゆ", "ユ", "よ", "ヨ", "ら", "ラ", "わ", "ワ",
            "り", "リ", "る", "ル", "れ", "レ", "ろ", "ロ", "を", "ヲ",
        )
    }

    class Arabic : NameGenerator("arabic") {
        override val elements = listOf(
            "ا", "ﺏ", "ﺕ", "ﺙ", "ﺝ", "ﺡ", "ﺥ", "ﺩ", "ﺫ", "ﺭ", "ﺯ", "ﺱ", "ﺵ", "ﺹ",
            "ﺽ", "ﻁ", "ﻅ", "ﻉ", "ﻍ", "ف", "ﻕ", "ﻙ", "ﻝ", "ﻡ", "ن", "ﻩ", "ﻭ", "ﻱ",
        )
    }

    class Uyghur : NameGenerator("uyghur") {
        override val elements = listOf(
            "ياخشى",
            "ئۇرۇمچى",
            "قاراماي",
            "تۇرپان",
            "قۇمۇل",
            "سانجى",
            "بۆرتالا",
            "بايىنغولىن",
            "قىزىلسۇ",
            "ئىلى",
            "ئاقسۇ",
            "قەشقەر",
            "خوتەن",
            "تارباغاتاي",
            "ئالتاي",
            "شىخەنزە",
            "ئارال",
            "تۇمشۇق",
            "ۋۇجياچۈ",
            "بەيتۈن",
            "باشئەگىم",
            "قوشئۆگۈز",
            "كۆكدالا",
            "قۇرۇمقاش",
            "خۇياڭخې",
            "شىنشىڭ",
            "بەيياڭ",
        )
    }

    class Confuse : NameGenerator("confuse") {
        override val elements = listOf("i", "I", "l", "1")
    }

    class Custom : NameGenerator("custom") {
        override val elements = kotlin.run {
            val charList = mutableListOf<String>()
            Configs.Settings.customDictionary.forEach {
                if (it.isNotEmpty()) charList.add(it)
            }
            charList.toSet().toList().ifEmpty { (('a'..'z') + ('A'..'Z')).map { it.toString() } }
        }
    }

    companion object {
        fun getByName(name: String): NameGenerator =
            when (name.lowercase()) {
                "alphabet" -> Alphabet()
                "arabic" -> Arabic()
                "blank" -> Blank()
                "chinese" -> Chinese()
                "confuse" -> Confuse()
                "custom" -> Custom()
                "japanese" -> Japanese()
                "uyghur" -> Uyghur()
                else -> Alphabet()
            }
    }

}