package net.spartanb312.grunt.process.hierarchy.krypton.info

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap
import java.util.concurrent.atomic.AtomicInteger

class NameCoder {
    private val set = Object2IntLinkedOpenHashMap<String>()
    private val inc = AtomicInteger()
    fun getCode(string: String): Int {
        return set.getOrPut(string) { inc.getAndIncrement() }
    }

    fun clear() {
        inc.set(0)
        set.clear()
    }
}