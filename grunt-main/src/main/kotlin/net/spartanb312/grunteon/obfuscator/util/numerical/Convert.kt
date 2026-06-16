package net.spartanb312.grunteon.obfuscator.util.numerical

import java.util.Locale

fun Double.asLong(): Long = java.lang.Double.doubleToRawLongBits(this)

fun Float.asInt(): Int = java.lang.Float.floatToRawIntBits(this)

fun formatInteger(value: Int): String {
    return String.format(Locale.US, "%,d", value)
}

fun formatInteger(value: Long): String {
    return String.format(Locale.US, "%,d", value)
}