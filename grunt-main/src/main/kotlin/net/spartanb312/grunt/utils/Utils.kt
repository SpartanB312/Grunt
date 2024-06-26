package net.spartanb312.grunt.utils

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
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