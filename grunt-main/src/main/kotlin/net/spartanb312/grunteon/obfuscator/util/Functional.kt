package net.spartanb312.grunteon.obfuscator.util

import kotlin.contracts.contract

inline fun <T> T.runIf(cond: Boolean, block: T.() -> T): T {
    contract {
        callsInPlace(block, kotlin.contracts.InvocationKind.AT_MOST_ONCE)
    }
    return if (cond) block(this) else this
}