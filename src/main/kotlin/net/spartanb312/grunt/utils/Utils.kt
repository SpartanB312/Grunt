package net.spartanb312.grunt.utils

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import kotlin.random.Random

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