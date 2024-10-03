package net.spartanb312.grunt.event

interface ICancellable {
    var cancelled: Boolean

    fun cancel() {
        cancelled = true
    }
}

abstract class CancellableEvent : ICancellable, Event {
    override var cancelled = false
        set(value) {
            field = field || value
        }
}

open class ListenerOwner : IListenerOwner {
    override val listeners = ArrayList<Listener>()
    override val parallelListeners = ArrayList<ParallelListener>()
}

interface AlwaysListening : IListenerOwner {
    override fun register(listener: Listener) {
        listener.eventBus.subscribe(listener)
    }

    override fun register(listener: ParallelListener) {
        listener.eventBus.subscribe(listener)
    }

    override fun subscribe() {

    }

    override fun unsubscribe() {

    }
}

interface IListenerOwner {
    val listeners: ArrayList<Listener>
    val parallelListeners: ArrayList<ParallelListener>

    fun register(listener: Listener) {
        listeners.add(listener)
    }

    fun register(listener: ParallelListener) {
        parallelListeners.add(listener)
    }

    fun subscribe() {
        for (listener in listeners) {
            listener.eventBus.subscribe(listener)
        }
        for (listener in parallelListeners) {
            listener.eventBus.subscribe(listener)
        }
    }

    fun unsubscribe() {
        for (listener in listeners) {
            listener.eventBus.unsubscribe(listener)
        }
        for (listener in parallelListeners) {
            listener.eventBus.unsubscribe(listener)
        }
    }
}

interface Event : EventPosting {
    fun post() {
        post(this)
    }
}

interface EventPosting {
    val eventBus: EventBus

    fun post(event: Any)
}