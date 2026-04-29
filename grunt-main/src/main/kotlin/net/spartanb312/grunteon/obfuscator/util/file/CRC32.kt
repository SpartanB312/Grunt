package net.spartanb312.grunteon.obfuscator.util.file

import org.apache.commons.rng.UniformRandomProvider
import sun.misc.Unsafe
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.zip.CRC32
import java.util.zip.ZipOutputStream


fun ZipOutputStream.corruptCRC32(randomGen: UniformRandomProvider) {
    //val field = ZipOutputStream::class.java.getDeclaredField("crc")
    //field.isAccessible = true
    //field[this] = object : CRC32() {
    //    override fun update(bytes: ByteArray, i: Int, length: Int) {}
    //    override fun getValue(): Long {
    //        return randomGen.nextInt(Int.MAX_VALUE - 1).toLong()
    //    }
    //}
    ZipCrcFinalSetter.setCrc(this, object : CRC32() {
        override fun update(bytes: ByteArray, i: Int, length: Int) {}
        override fun getValue(): Long {
            return randomGen.nextInt(Int.MAX_VALUE - 1).toLong()
        }
    })
}

object ZipCrcFinalSetter {
    fun setCrc(zos: ZipOutputStream?, value: CRC32?) {
        try {
            val f = ZipOutputStream::class.java.getDeclaredField("crc")
            val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe")
            unsafeField.setAccessible(true)
            val unsafe = unsafeField.get(null)
            val objectFieldOffsetMethod: Method = Unsafe::class.java.getMethod("objectFieldOffset", Field::class.java)
            val offset = objectFieldOffsetMethod.invoke(unsafe, f) as Long
            val putObjectMethod: Method = Unsafe::class.java.getMethod(
                "putObject",
                Any::class.java,
                Long::class.javaPrimitiveType,
                Any::class.java
            )
            putObjectMethod.invoke(unsafe, zos, offset, value)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}