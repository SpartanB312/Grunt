package net.spartanb312.grunteon.obfuscator.pipeline

import net.spartanb312.grunteon.obfuscator.Grunteon

abstract class ExecutionStage(val name: String) {
    abstract fun Grunteon.execute()
}

fun ExecutionStage.execute(grunteon: Grunteon) = grunteon.execute()