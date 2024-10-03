package net.spartanb312.grunt.event

import kotlinx.coroutines.launch
import net.spartanb312.grunt.utils.thread.MainScope

const val DEFAULT_LISTENER_PRIORITY = 0

inline fun <reified E : Event> IListenerOwner.listener(
    noinline function: (E) -> Unit
) = listener(this, E::class.java, DEFAULT_LISTENER_PRIORITY, false, function)

inline fun <reified E : Event> IListenerOwner.listener(
    priority: Int,
    noinline function: (E) -> Unit
) = listener(this, E::class.java, priority, false, function)

inline fun <reified E : Event> IListenerOwner.listener(
    alwaysListening: Boolean,
    noinline function: (E) -> Unit
) = listener(this, E::class.java, DEFAULT_LISTENER_PRIORITY, alwaysListening, function)

inline fun <reified E : Event> IListenerOwner.listener(
    priority: Int,
    alwaysListening: Boolean,
    noinline function: (E) -> Unit
) = listener(this, E::class.java, priority, alwaysListening, function)


inline fun <reified E : Event> IListenerOwner.parallelListener(
    noinline function: suspend (E) -> Unit
) = parallelListener(this, E::class.java, false, function)

inline fun <reified E : Event> IListenerOwner.parallelListener(
    alwaysListening: Boolean,
    noinline function: suspend (E) -> Unit
) = parallelListener(this, E::class.java, alwaysListening, function)


inline fun <reified E : Event> IListenerOwner.concurrentListener(
    noinline function: suspend (E) -> Unit
) = concurrentListener(this, E::class.java, false, function)

inline fun <reified E : Event> IListenerOwner.concurrentListener(
    alwaysListening: Boolean,
    noinline function: suspend (E) -> Unit
) = concurrentListener(this, E::class.java, alwaysListening, function)

@Suppress("UNCHECKED_CAST")
fun <E : Event> listener(
    owner: IListenerOwner,
    eventClass: Class<E>,
    priority: Int,
    alwaysListening: Boolean,
    function: (E) -> Unit
) {
    val eventBus = getEventBus(eventClass)
    val listener = Listener(owner, eventBus, priority, function as (Any) -> Unit)

    if (alwaysListening) eventBus.subscribe(listener)
    else owner.register(listener)
}

@Suppress("UNCHECKED_CAST")
fun <E : Event> parallelListener(
    owner: IListenerOwner,
    eventClass: Class<E>,
    alwaysListening: Boolean,
    function: suspend (E) -> Unit
) {
    val eventBus = getEventBus(eventClass)
    val listener = ParallelListener(owner, eventBus, function as suspend (Any) -> Unit)

    if (alwaysListening) eventBus.subscribe(listener)
    else owner.register(listener)
}

@Suppress("UNCHECKED_CAST")
fun <E : Event> concurrentListener(
    owner: IListenerOwner,
    eventClass: Class<E>,
    alwaysListening: Boolean,
    function: suspend (E) -> Unit
) {
    val eventBus = getEventBus(eventClass)
    val listener = Listener(owner, eventBus, Int.MAX_VALUE) { MainScope.launch { function.invoke(it as E) } }

    if (alwaysListening) eventBus.subscribe(listener)
    else owner.register(listener)
}

private fun getEventBus(eventClass: Class<out Event>): EventBus {
    return try {
        eventClass.instance!!
    } catch (e: Exception) {
        eventClass.getDeclaredField("Companion")[null] as EventPosting
    }.eventBus
}

class Listener(
    owner: Any,
    eventBus: EventBus,
    priority: Int,
    function: (Any) -> Unit
) : AbstractListener<(Any) -> Unit>(owner, eventBus, priority, function)

class ParallelListener(
    owner: Any,
    eventBus: EventBus,
    function: suspend (Any) -> Unit
) : AbstractListener<suspend (Any) -> Unit>(owner, eventBus, DEFAULT_LISTENER_PRIORITY, function)

sealed class AbstractListener<F>(
    owner: Any,
    val eventBus: EventBus,
    val priority: Int,
    val function: F
) {
    val ownerName: String = owner.javaClass.simpleName
}

@Suppress("UNCHECKED_CAST")
inline val <T> Class<out T>.instance: T?
    get() = try {
        this.getDeclaredField("INSTANCE")[null] as T?
    } catch (ignore: Exception) {
        null
    }
