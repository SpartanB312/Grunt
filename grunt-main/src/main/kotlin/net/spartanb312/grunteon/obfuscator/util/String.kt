package net.spartanb312.grunteon.obfuscator.util

import org.apache.commons.rng.UniformRandomProvider

val blanks = listOf(
    '\u0020', '\u00a0', '\u1680', '\u180e', '\u2000', '\u2001', '\u2002', '\u2003', '\u2004',
    '\u2005', '\u2006', '\u2007', '\u2008', '\u2009', '\u200a', '\u200b', '\u200c', '\u200d',
    '\u200e', '\u200f', '\u202f', '\u205f', '\u3000', '\ufeff'
)

private val BLANK_STRINGS = arrayOf(
    buildString { repeat(Short.MAX_VALUE.toInt() / 2) { append(blanks.random()) } },
    buildString { repeat(Short.MAX_VALUE.toInt() / 2) { append(blanks.random()) } },
    buildString { repeat(Short.MAX_VALUE.toInt() / 2) { append(blanks.random()) } },
    buildString { repeat(Short.MAX_VALUE.toInt() / 2) { append(blanks.random()) } },
    buildString { repeat(Short.MAX_VALUE.toInt() / 2) { append(blanks.random()) } },
)

val massiveString = buildString { repeat(Short.MAX_VALUE.toInt() - 1) { append(" ") } }
val massiveBlankString: String get() = BLANK_STRINGS.random()

val nextBadKeyword get() = badKeywords.random()

private val badKeywords = arrayOf(
    "public",
    "private",
    "protected",
    "static",
    "final",
    "native",
    "class",
    "interface",
    "enum",
    "abstract",
    "int",
    "float",
    "double",
    "short",
    "byte",
    "long",
    "synchronized",
    "strictfp",
    "volatile",
    "transient",
    "return",
    "for",
    "while",
    "switch",
    "break"
)

private val charSet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"

fun UniformRandomProvider.getRandomString(length: Int): String {
    var str = ""
    repeat(length) {
        str += charSet[(charSet.length * nextInt(0, 100) / 100f).toInt()]
    }
    return str
}

inline val String.splash get() = replace(".", "/")
inline val String.dot get() = replace("/", ".")
