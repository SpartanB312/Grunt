package net.spartanb312.grunteon.obfuscator.util.cryptography

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.Transformer
import org.apache.commons.rng.RestorableUniformRandomProvider
import org.apache.commons.rng.simple.RandomSource
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

context(instance: Grunteon)
fun getSeed(vararg append: String): ByteArray {
    return MessageDigest.getInstance("SHA-256")
        .digest(append.fold(instance.globalConfig.baseSeed(), String::plus).toByteArray(StandardCharsets.UTF_8))
}

context(instance: Grunteon)
fun Transformer<*>.getSeed(vararg append: String): ByteArray {
    return MessageDigest.getInstance("SHA-256")
        .digest((append.fold(transformerSeed, String::plus)).toByteArray(StandardCharsets.UTF_8))
}

@Suppress("FunctionName")
fun Xoshiro256PPRandom(bytes: ByteArray): RestorableUniformRandomProvider = RandomSource.XO_SHI_RO_256_PP.create(bytes)