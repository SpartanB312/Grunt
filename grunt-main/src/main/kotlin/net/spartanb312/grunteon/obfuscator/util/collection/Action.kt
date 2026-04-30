package net.spartanb312.grunteon.obfuscator.util.collection

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import java.util.*

const val SHUFFLE_THRESHOLD = 5

inline fun <reified T> Iterable<T>.shuffled(randomGen: UniformRandomProvider): List<T> =
    toMutableList().apply { shuffle(randomGen) }

inline fun <reified T> MutableList<T>.shuffle(rnd: UniformRandomProvider) {
    val size: Int = size
    if (size < SHUFFLE_THRESHOLD || this is RandomAccess) {
        for (i in size downTo 2) Collections.swap(this, i - 1, rnd.nextInt(i))
    } else {
        val arr = this.toTypedArray()
        for (i in size downTo 2) swap(arr, i - 1, rnd.nextInt(i))
        val it: MutableListIterator<T> = this.listIterator()
        for (e in arr) {
            it.next()
            it.set(e)
        }
    }
}

fun <T> Sequence<T>.shuffled(random: UniformRandomProvider): Sequence<T> = sequence {
    val buffer = toMutableList()
    while (buffer.isNotEmpty()) {
        val j = random.nextInt(buffer.size)
        val last = buffer.removeLast()
        val value = if (j < buffer.size) buffer.set(j, last) else last
        yield(value)
    }
}

fun <T> swap(arr: Array<T>, i: Int, j: Int) {
    val tmp = arr[i]
    arr[i] = arr[j]
    arr[j] = tmp
}

fun <T> Collection<T>.random(randomGen: UniformRandomProvider): T {
    if (isEmpty()) throw NoSuchElementException("Collection is empty.")
    return elementAt(randomGen.nextInt(size))
}

class FastObjectArrayList<T> : ObjectArrayList<T> {

    override var size: Int
        get() = super.size
        set(value) {
            super.size = value
        }

    fun clearFast() {
        this.size = 0
    }

    constructor(arr: Array<T>, dummy: Boolean) : super(arr, true)

    companion object {
        inline operator fun <reified T> invoke(): FastObjectArrayList<T> = FastObjectArrayList(emptyArray(), true)

        @Suppress("UNCHECKED_CAST")
        inline operator fun <reified T> invoke(initialCapacity: Int): FastObjectArrayList<T> =
            FastObjectArrayList(arrayOfNulls<T>(initialCapacity) as Array<T>, true)
    }
}

fun InsnList.toListFast(prev: FastObjectArrayList<AbstractInsnNode> = FastObjectArrayList(this.size())): FastObjectArrayList<AbstractInsnNode> {
    prev.size = 0
    prev.ensureCapacity(this.size())
    prev.size = this.size()
    val arr = prev.elements()
    this.forEachIndexed { index, node ->
        arr[index] = node
    }
    @Suppress("UNCHECKED_CAST")
    return prev
}

inline fun <V> Int2ObjectMap<V>.forEachFast(crossinline action: (Int, V) -> Unit) {
    if (this is Int2ObjectOpenHashMap<V>) {
        this.int2ObjectEntrySet().fastForEach {
            action(it.intKey, it.value)
        }
    } else {
        this.int2ObjectEntrySet().forEach { entry ->
            action(entry.intKey, entry.value)
        }
    }
}