package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import kotlin.io.path.extension
import kotlin.io.path.pathString
import kotlin.io.path.toPath

fun readTestClasses(klass: Class<*>, config: ObfConfig = ObfConfig()): Grunteon {
    val path = klass.protectionDomain.codeSource.location.toURI().toPath()
    check(path.extension == "jar")
    val instance = Grunteon.create(config.copy(input = path.pathString))
    return instance
}