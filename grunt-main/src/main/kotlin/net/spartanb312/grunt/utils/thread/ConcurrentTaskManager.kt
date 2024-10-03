package net.spartanb312.grunt.utils.thread

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight concurrent task manager by using coroutine
 * Support Delay task and Repeat task
 */
fun ConcurrentTaskManager(
    name: String,
    threads: Int = Runtime.getRuntime().availableProcessors(),
    lite: Boolean = true
): ConcurrentTaskManager = ConcurrentTaskManager(name, lite, newCoroutineScope(threads, name))

class ConcurrentTaskManager(
    name: String,
    lite: Boolean = true,
    scope: CoroutineScope,
) : CoroutineScope by scope {

    private val working = AtomicBoolean(true)
    private val scheduledTasks = CopyOnWriteArraySet<Pair<suspend CoroutineScope.() -> Unit, Long>>()//Task, StartTime
    private val repeatUnits = CopyOnWriteArraySet<RepeatUnit>()

    init {
        //launch a daemon thread for those scheduled tasks
        object : Thread("$name-Daemon") {
            override fun run() {
                while (working.get()) {
                    val currentTime = System.currentTimeMillis()
                    scheduledTasks.removeIf { (task, startTime) ->
                        if (currentTime > startTime) {
                            launch(block = task)
                            true
                        } else false
                    }
                    repeatUnits.removeIf {
                        if (it.isDead) true
                        else {
                            if (!it.isSuspended) it.invoke()
                            false
                        }
                    }
                    if (lite) sleep(1)
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    //Delay task
    fun runLater(
        delayTime: Int,
        block: suspend CoroutineScope.() -> Unit
    ) = scheduledTasks.add(Pair(block, System.currentTimeMillis() + delayTime))

    //Repeat task
    fun runRepeat(
        delayTime: Int,
        suspended: Boolean = false,
        block: suspend CoroutineScope.() -> Unit
    ): RepeatUnit = RepeatUnit(delayTime, suspended, block).also { repeatUnits.add(it) }

    inner class RepeatUnit(
        private var delayTime: Int,
        suspended: Boolean = false,
        private val block: suspend CoroutineScope.() -> Unit
    ) {
        private val suspended = AtomicBoolean(suspended)
        private val isAlive = AtomicBoolean(true)
        private val isRunning = AtomicBoolean(false)
        private var nextStartTime = 0L

        val isDead get() = !isAlive.get()
        val isSuspended get() = suspended.get()

        fun suspend() = suspended.set(true)

        fun resume() = suspended.set(false)

        fun stop() = isAlive.set(false)

        fun resetDelay(delayTime: Int) {
            this.delayTime = delayTime
        }

        fun invoke() {
            if (System.currentTimeMillis() > nextStartTime) {
                if (!isRunning.getAndSet(true)) {
                    nextStartTime = System.currentTimeMillis() + delayTime
                    launch {
                        kotlin.runCatching { block() }.onFailure { it.printStackTrace() }
                        isRunning.set(false)
                    }
                }
            }
        }
    }

    fun shutdown() {
        working.set(false)
        this.coroutineContext.cancel()
    }

    fun available(): Boolean = working.get()

}

private val taskManagerId = AtomicInteger()
val nextTaskManagerId = taskManagerId.getAndIncrement()

inline fun <T> taskManager(
    name: String = "DefaultTaskManager-$nextTaskManagerId",
    crossinline block: suspend ConcurrentTaskManager.() -> T
) = ConcurrentTaskManager(name).apply {
    runBlocking {
        block()
        shutdown()
    }
}
