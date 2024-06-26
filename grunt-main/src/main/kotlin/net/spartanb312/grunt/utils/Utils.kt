package net.spartanb312.grunt.utils

import java.util.zip.CRC32
import java.util.zip.ZipOutputStream
import kotlin.random.Random

fun ZipOutputStream.corruptCRC32() {
    val field = ZipOutputStream::class.java.getDeclaredField("crc")
    field.isAccessible = true
    field[this] = object : CRC32() {
        override fun update(bytes: ByteArray, i: Int, length: Int) {}
        override fun getValue(): Long {
            return Random.nextInt(0, Int.MAX_VALUE).toLong()
        }
    }
}

inline val String.splash get() = replace(".", "/")
inline val String.dot get() = replace("/", ".")

fun String.isNotExcludedIn(list: List<String>): Boolean =
    !isExcludedIn(list)

fun String.isExcludedIn(list: List<String>): Boolean {
    return list.any { this.startsWith(it) }
}

inline fun <T> Sequence<T>.forEachThis(action: T.() -> Unit): Unit {
    for (element in this) action(element)
}

inline fun <T> Collection<T>.forEachThis(action: T.() -> Unit): Unit {
    for (element in this) action(element)
}

inline fun <T> Iterable<T>.forEachThis(action: T.() -> Unit): Unit {
    for (element in this) action(element)
}

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