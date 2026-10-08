/* SPDX-License-Identifier: PolyForm-Strict-1.0.0 */
package net.spartanb312.grunt.yapyap.runtime

import it.unisa.dia.gas.jpbc.Pairing
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory
import it.unisa.dia.gas.plaf.jpbc.pairing.parameters.PropertiesParameters
import it.unisa.dia.gas.plaf.jpbc.pbc.PBCPairingFactory
import org.junit.Assume.assumeTrue
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.util.Base64
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

class AbeRuntimeRegressionTest {
    @Test
    fun decodedParametersAreImmutableBoundedAndDoNotRetainOversizedInputs() {
        val encoded = encode("type a\nq 11\nr 5\nh 2\n")
        val first = AbeRuntimeSupport.parameters(encoded)
        assertSame(first, AbeRuntimeSupport.parameters(encoded))
        assertSame(first.getBigInteger("q"), first.getBigInteger("q"))
        val properties = first as PropertiesParameters
        assertFailsWith<UnsupportedOperationException> { properties.put("q", "7") }
        assertFailsWith<UnsupportedOperationException> { properties.remove("q") }
        assertFailsWith<UnsupportedOperationException> { properties.load("type a".byteInputStream()) }
        val serialized = ByteArrayOutputStream().also { output ->
            java.io.ObjectOutputStream(output).use { it.writeObject(first) }
        }.toByteArray()
        val restored = java.io.ObjectInputStream(serialized.inputStream()).use { it.readObject() }
        assertEquals(first.toString(), restored.toString())
        repeat(40) { AbeRuntimeSupport.parameters(encode("type a\nq ${it + 100}\n")) }
        assertEquals(AbeRuntimeSupport.PARAMETER_CACHE_SIZE, parameterCacheSize())
        val oversized = encode("#" + "x".repeat(AbeRuntimeSupport.MAX_ENCODED_PARAMETER_CHARS) + "\ntype a\n")
        assertNotSame(AbeRuntimeSupport.parameters(oversized), AbeRuntimeSupport.parameters(oversized))
        assertEquals(AbeRuntimeSupport.PARAMETER_CACHE_SIZE, parameterCacheSize())
        repeat(2) { assertFailsWith<IllegalArgumentException> { AbeRuntimeSupport.parameters("invalid base64!") } }
    }

    @Test
    fun coefficientCacheMatchesFieldArithmeticAndIsImmutable() {
        val pairing = StringAbeRuntime.readPairing(parametersEncoded, false)
        for (n in listOf(1, 2, 5, 16, 64, 65)) {
            for (i in 1..n) {
                val expected = originalCoefficient(pairing, i, n)
                assertTrue(expected.isEqual(AbeRuntimeSupport.lagrangeCoefficient(pairing, i, n)), "n=$n i=$i")
            }
        }
        assertEquals(listOf(5, -10, 10, -5, 1).map { BigInteger.valueOf(it.toLong()) }, AbeRuntimeSupport.coefficients(5))
        assertSame(AbeRuntimeSupport.coefficients(5), AbeRuntimeSupport.coefficients(5))
        assertFailsWith<UnsupportedOperationException> { AbeRuntimeSupport.coefficients(5).clear() }
        assertFailsWith<IllegalArgumentException> { AbeRuntimeSupport.coefficients(65) }
    }

    @Test
    fun parallelPairingsAreNeverSharedAndGlobalBackendSettingsDoNotChange() {
        val factory = PairingFactory.getInstance()
        val nativeBefore = factory.isUsePBCWhenPossible
        val reuseBefore = factory.isReuseInstance
        val executor = Executors.newFixedThreadPool(4)
        try {
            val pairings = executor.invokeAll(List(8) { Callable { StringAbeRuntime.readPairing(parametersEncoded, false) } })
                .map { it.get() }
            for (i in pairings.indices) for (j in 0 until i) assertNotSame(pairings[i], pairings[j])
            assertNotSame(pairings[0], NumberAbeRuntime.readPairing(parametersEncoded, false))
        } finally { executor.shutdownNow() }
        assertEquals(nativeBefore, factory.isUsePBCWhenPossible)
        assertEquals(reuseBefore, factory.isReuseInstance)
    }

    @Test
    fun stringAndNumberPoolsRoundTripAndStillEnforceShapeKindAndGcm() {
        roundTrip(false)
    }

    @Test
    fun nativeBackendRoundTripsWhenActuallyInstalled() {
        // A missing PBC installation is a reported skip, not an empty green native test.
        assumeTrue("PBC native library is not installed", PBCPairingFactory.isPBCAvailable())
        roundTrip(true)
    }

    @Test
    fun optionalNativeFallsBackWithoutChangingParameters() {
        val pairing = StringAbeRuntime.readPairing(parametersEncoded, true)
        assertEquals(parameters.getBigInteger("r"), pairing.zr.order)
    }

    @Test
    fun cacheLifetimeIsIsolatedToTheRuntimeClassloader() {
        val name = AbeRuntimeSupport::class.java.name
        val prefix = name + "$"
        fun loader() = object : ClassLoader(AbeRuntimeSupport::class.java.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name != AbeRuntimeSupport::class.java.name && !name.startsWith(prefix)) return super.loadClass(name, resolve)
                synchronized(getClassLoadingLock(name)) {
                    var loaded = findLoadedClass(name)
                    if (loaded == null) {
                        val resource = name.replace('.', '/') + ".class"
                        val bytes = parent.getResourceAsStream(resource)!!.use { it.readBytes() }
                        loaded = defineClass(name, bytes, 0, bytes.size)
                    }
                    if (resolve) resolveClass(loaded)
                    return loaded
                }
            }
        }
        val first = Class.forName(name, true, loader())
        val second = Class.forName(name, true, loader())
        fun cached(type: Class<*>): Any = type.getDeclaredMethod("parameters", String::class.java).apply { isAccessible = true }
            .invoke(null, parametersEncoded)
        assertNotSame(first.classLoader, second.classLoader)
        assertNotSame(cached(first), cached(second))
    }

    private fun roundTrip(native: Boolean) {
        val salt = encode("fixed test shape salt")
        val shape = StringAbeRuntime.shapeAttribute(AbeRuntimeRegressionTest::class.java, salt)
        val stringAttributes = arrayOf("app:test", "build:test", "kind:string", "pool:strings", shape)
        val stringPayload = StringAbeRuntime.buildPool(stringAttributes, arrayOf("alpha", "βeta"), native, parameters)
        val pairing = StringAbeRuntime.readPairing(stringPayload[1], native)
        val secret = StringAbeRuntime.readSecretKey(pairing, decode(stringPayload[2]))
        val cipher = StringAbeRuntime.readCipherText(pairing, decode(stringPayload[3]))
        val key = StringAbeRuntime.dataKey(StringAbeRuntime.decrypt(pairing, secret, cipher, shape))
        assertContentEquals(arrayOf("alpha", "βeta"), StringAbeRuntime.readStringBlob(
            StringAbeRuntime.decryptBlob(key, decode(stringPayload[4]))))
        repeat(2) {
            assertFailsWith<IllegalStateException> { StringAbeRuntime.decrypt(pairing, secret, cipher, "shape:wrong") }
        }
        val corrupt = decode(stringPayload[4]).also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertFailsWith<javax.crypto.AEADBadTagException> { StringAbeRuntime.decryptBlob(key, corrupt) }

        val blob = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { data ->
                data.writeInt(2); data.writeInt(Int.MIN_VALUE); data.writeInt(Int.MAX_VALUE)
                data.writeInt(1); data.writeLong(Long.MIN_VALUE)
                data.writeInt(2); data.writeInt(Int.MIN_VALUE); data.writeInt(0x7fc00123)
                data.writeInt(2); data.writeLong(Long.MIN_VALUE); data.writeLong(0x7ff8000000000123L)
            }
        }.toByteArray()
        val numberPayload = NumberAbeRuntime.buildPool(
            arrayOf("app:test", "build:test", "kind:number", "pool:numbers", shape), blob, native, parameters)
        val numberPairing = NumberAbeRuntime.readPairing(numberPayload[1], native)
        val numberSecret = NumberAbeRuntime.readSecretKey(numberPairing, decode(numberPayload[2]))
        val numberCipher = NumberAbeRuntime.readCipherText(numberPairing, decode(numberPayload[3]))
        val numberKey = NumberAbeRuntime.dataKey(NumberAbeRuntime.decrypt(numberPairing, numberSecret, numberCipher, shape))
        val plain = NumberAbeRuntime.decryptBlob(numberKey, decode(numberPayload[4]))
        assertContentEquals(blob, plain)
        val values = NumberAbeRuntime.readNumberBlob(plain)
        assertContentEquals(intArrayOf(Int.MIN_VALUE, Int.MAX_VALUE), values[0] as IntArray)
        assertContentEquals(longArrayOf(Long.MIN_VALUE), values[1] as LongArray)
        assertContentEquals(intArrayOf(Int.MIN_VALUE, 0x7fc00123), (values[2] as FloatArray).map { it.toRawBits() }.toIntArray())
        assertContentEquals(longArrayOf(Long.MIN_VALUE, 0x7ff8000000000123L), (values[3] as DoubleArray).map { it.toRawBits() }.toLongArray())
        assertFailsWith<IllegalStateException> {
            NumberAbeRuntime.decrypt(numberPairing, numberSecret, numberCipher, "shape:wrong")
        }
        // Same wire format and curve do not make a key from another pool kind satisfy this policy.
        val crossKindCipher = StringAbeRuntime.readCipherText(pairing, decode(numberPayload[3]))
        val failure = assertFailsWith<IllegalStateException> { StringAbeRuntime.decrypt(pairing, secret, crossKindCipher, shape) }
        assertTrue(failure.message!!.contains("Missing CP-ABE private key attribute"))
    }

    private fun parameterCacheSize(): Int = (AbeRuntimeSupport::class.java.getDeclaredField("PARAMETERS")
        .apply { isAccessible = true }.get(null) as Map<*, *>).size

    private fun originalCoefficient(pairing: Pairing, i: Int, n: Int): it.unisa.dia.gas.jpbc.Element {
        val numerator = pairing.zr.newOneElement()
        val denominator = pairing.zr.newOneElement()
        for (j in 1..n) if (j != i) {
            numerator.mul(pairing.zr.newElement(-j))
            denominator.mul(pairing.zr.newElement(i - j))
        }
        return numerator.div(denominator).immutable
    }

    companion object {
        // Small curves ONLY in tests for execution time. Production config defaults remain 256/1536.
        private val parameters by lazy { StringAbeRuntime.buildParams(64, 256) }
        private val parametersEncoded by lazy { encode(parameters.toString()) }
        private fun encode(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())
        private fun decode(text: String) = Base64.getDecoder().decode(text)
    }
}
