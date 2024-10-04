package net.spartanb312.grunt.utils

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.zip.CRC32
import java.util.zip.ZipOutputStream
import kotlin.random.Random

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

fun String.notInList(list: List<String>, startWith: Boolean = true): Boolean =
    !inList(list, startWith)

fun String.inList(list: List<String>, startWith: Boolean = true): Boolean {
    return list.any { if (startWith) this.startsWith(it) else this == it }
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

fun String.getReturnType(): Int = when (Type.getReturnType(this).sort) {
    Type.BOOLEAN -> Opcodes.IRETURN
    Type.CHAR -> Opcodes.IRETURN
    Type.BYTE -> Opcodes.IRETURN
    Type.SHORT -> Opcodes.IRETURN
    Type.INT -> Opcodes.IRETURN
    Type.LONG -> Opcodes.LRETURN
    Type.FLOAT -> Opcodes.FRETURN
    Type.DOUBLE -> Opcodes.DRETURN
    Type.VOID -> Opcodes.RETURN
    else -> Opcodes.ARETURN
}

fun Type.getLoadType(): Int = when (sort) {
    Type.BOOLEAN -> Opcodes.ILOAD
    Type.CHAR -> Opcodes.ILOAD
    Type.BYTE -> Opcodes.ILOAD
    Type.SHORT -> Opcodes.ILOAD
    Type.INT -> Opcodes.ILOAD
    Type.LONG -> Opcodes.LLOAD
    Type.FLOAT -> Opcodes.FLOAD
    Type.DOUBLE -> Opcodes.DLOAD
    else -> Opcodes.ALOAD
}

fun getRandomString(length: Int): String {
    val charSet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
    var str = ""
    repeat(length) {
        str += charSet[(charSet.length * Random.nextInt(0, 100) / 100f).toInt()]
    }
    return str
}

// Return true if ver1 >= ver2
fun compareVersion(ver1: String, ver2: String): Boolean {
    if (ver1 == ver2) return true
    val array1 = ver1.split(".").map { it.toInt() }
    val array2 = ver2.split(".").map { it.toInt() }
    if (array1[0] > array2[0]) return true
    else if (array1[0] < array2[0]) return false
    if (array1[1] > array2[1]) return true
    else if (array1[1] < array2[1]) return false
    if (array1[2] > array2[2]) return true
    else if (array1[2] < array2[2]) return false
    return false
}