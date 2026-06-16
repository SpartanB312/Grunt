package net.spartanb312.grunteon.obfuscator.util

import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kotlinx.coroutines.*
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.CoroutineContext

@Suppress("ATOMIC_REF_WITHOUT_CONSISTENT_IDENTITY", "ATOMIC_REF_CALL_ARGUMENT_WITHOUT_CONSISTENT_IDENTITY")
class LWWSP(val workerCount: Int, val threadConfigure: (Thread) -> Unit = {}) : CoroutineDispatcher() {
    private val workerCountMinus1 = workerCount - 1

    private val workers = List(workerCount) { Worker(this, it).also(threadConfigure) }
    private val activeWorkerCount = AtomicInteger()

    private val isAccepting = AtomicBoolean(true)
    private val isRunning = AtomicBoolean(true)

    init {
        workers.forEach { it.start() }
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val task = RecursiveTask { block.run() }
        task.submit()
    }

    fun shutdown() {
        isAccepting.set(false)
        isRunning.set(false)
        workers.forEach {
            it.taskQueue.stopped = true
            it.taskQueue.wake()
        }
        workers.forEach { it.join() }
    }

    fun shutdownNow() {
        isAccepting.set(false)
        isRunning.set(false)
        workers.forEach {
            it.taskQueue.stopped = true
            it.taskQueue.wake()
        }
        workers.forEach {
            it.interrupt()
            it.join()
        }
    }

    private fun checkState() {
        check(isAccepting.get()) { "Pool is not accepting new tasks" }
    }

    private class TaskQueue {
        private val queueExclusive = ObjectArrayFIFOQueue<AbstractTask<*>>()
        private val queueShared = ObjectArrayFIFOQueue<AbstractTask<*>>()
        private val lock = ReentrantLock()
        private val condition = lock.newCondition()
        @Volatile
        var stopped = false

        private fun ObjectArrayFIFOQueue<AbstractTask<*>>.tryDequeue(): AbstractTask<*>? {
            return if (isEmpty) null else dequeue()
        }

        val sharedSize get() = queueShared.size()
        val size get() = queueShared.size() + queueExclusive.size()

        fun enqueueShared(tasks: Collection<AbstractTask<*>>) {
            lock.withLock {
                tasks.forEach(queueShared::enqueue)
                condition.signalAll()
            }
        }

        fun enqueueShared(task: AbstractTask<*>) {
            lock.withLock {
                queueShared.enqueue(task)
                condition.signal()
            }
        }

        fun enqueueExclusive(task: AbstractTask<*>) {
            lock.withLock {
                queueExclusive.enqueue(task)
                condition.signal()
            }
        }

        fun dequeue(): AbstractTask<*>? {
            lock.lock()
            try {
                while (true) {
                    val task = queueExclusive.tryDequeue() ?: queueShared.tryDequeue()
                    if (task != null) return task
                    if (stopped) return null
                    condition.await()
                }
            } finally {
                lock.unlock()
            }
        }

        fun dequeue(time: Long, unit: TimeUnit): AbstractTask<*>? {
            lock.lock()
            try {
                val task = queueExclusive.tryDequeue() ?: queueShared.tryDequeue()
                if (task != null) return task
                if (stopped) return null
                condition.await(time, unit)
                return queueExclusive.tryDequeue() ?: queueShared.tryDequeue()
            } finally {
                lock.unlock()
            }
        }

        fun drainToShared(dst: MutableCollection<AbstractTask<*>>, limit: Int = Int.MAX_VALUE) {
            lock.withLock {
                var count = 0
                while (count < limit) {
                    val task = queueShared.tryDequeue() ?: break
                    dst.add(task)
                    count++
                }
            }
        }

        fun wake() {
            lock.withLock {
                condition.signalAll()
            }
        }

        fun signal() {
            lock.withLock {
                condition.signal()
            }
        }
    }

    private class Worker(val pool: LWWSP, val id: Int) : Thread("LWWSP-$id") {
        val taskQueue = TaskQueue()
        private val drainTemp = ObjectArrayList<AbstractTask<*>>()
        val random = Xoshiro256PPRandom("LWWSP-$id".toByteArray())

        fun randomOtherWorkerID(): Int {
            if (pool.workerCountMinus1 <= 0) return -1
            val victimIDOffset = random.nextInt(pool.workerCountMinus1)
            val victimId = (id + 1 + victimIDOffset) % pool.workerCount
            assert(victimId != id)
            return victimId
        }

        private fun steal() {
            if (pool.workerCountMinus1 <= 0) return
            yield()
            val victimIDBaseOffset = random.nextInt(pool.workerCountMinus1)

            for (i in 0..<pool.workerCountMinus1) {
                val victimIDOffset = (victimIDBaseOffset + i) % (pool.workerCountMinus1)
                val victimID = (id + 1 + victimIDOffset) % pool.workerCount
                val victim = pool.workers[victimID]
                val limit = victim.taskQueue.size / 2
                if (limit <= 0) continue
                drainTemp.ensureCapacity(limit)
                victim.taskQueue.drainToShared(drainTemp, limit)
                taskQueue.enqueueShared(drainTemp)
                drainTemp.clear()
                return
            }
        }

        override fun run() {
            pool.activeWorkerCount.incrementAndGet()
            while (pool.isRunning.get()) {
                val cTask = try {
                    taskQueue.dequeue()
                } catch (_: InterruptedException) {
                    null
                }
                if (!pool.isRunning.get()) break
                if (pool.workerCountMinus1 > 0 && taskQueue.sharedSize >= 1) {
                    pool.workers[randomOtherWorkerID()].taskQueue.signal()
                }
                if (cTask == null) {
                    steal()
                    continue
                }
                cTask.run(this)
            }
            pool.activeWorkerCount.decrementAndGet()
        }
    }

    fun interface IterativeAction {
        fun run(start: Int, end: Int)
    }

    fun interface ScopedIterativeAction<S> {
        fun S.run(start: Int, end: Int)
    }

    fun interface RecursiveAction<R> {
        fun run(): R
    }

    sealed interface Task<T> {
        val isCompleted: Boolean
        suspend fun await(pollingTimeout: Long = 1L): Result<T>
    }

    @OptIn(InternalCoroutinesApi::class)
    private sealed class AbstractTask<T> : Task<T> {
        private val completeResult = AtomicReference<Result<T>>(null)
        private val continuations = ConcurrentLinkedQueue<CancellableContinuation<Result<T>>>()

        override val isCompleted get() = completeResult.get() != null

        protected fun complete(result: Result<T>) {
            if (!completeResult.compareAndSet(null, result)) return
            resumeAllContinuations(result)
        }

        protected fun completeFailure(result: Result<T>) {
            if (!completeResult.compareAndSet(null, result)) {
                // Task was already completed (success or prior failure);
                // log this late exception but don't corrupt the stored result
                result.exceptionOrNull()?.printStackTrace()
                return
            }
            resumeAllContinuations(result)
        }

        private fun resumeAllContinuations(result: Result<T>) {
            while (true) {
                val cont = continuations.poll() ?: break
                val token = cont.tryResume(result)
                if (token != null) {
                    cont.completeResume(token)
                }
            }
        }

        final override suspend fun await(pollingTimeout: Long): Result<T> {
            // Fast path: already completed
            completeResult.get()?.let { return it }

            return suspendCancellableCoroutine { cont ->
                continuations.add(cont)
                cont.invokeOnCancellation { continuations.remove(cont) }

                // Double-check after registration to close the race window
                val result = completeResult.get()
                if (result != null) {
                    val token = cont.tryResume(result)
                    if (token != null) {
                        cont.completeResume(token)
                    }
                }
            }
        }

        abstract fun run(worker: Worker)

        abstract fun submit()
    }

    private inner class RecursiveTask<R>(private val action: RecursiveAction<R>) : AbstractTask<R>() {
        override fun run(worker: Worker) {
            try {
                val result = action.run()
                complete(Result.success(result))
            } catch (e: Throwable) {
                completeFailure(Result.failure(ExecutionException("Exception in worker ${worker.name}", e)))
            }
        }

        override fun submit() {
            checkState()
            val currentThread = Thread.currentThread()
            val worker = if (currentThread is Worker) {
                currentThread
            } else {
                val workerID = ThreadLocalRandom.current().nextInt(workerCount)
                workers[workerID]
            }
            worker.taskQueue.enqueueShared(this)
        }
    }

    private inner class ScopedIterativeTask<S>(
        private val size: Int,
        protected val batchSize: Int,
        private val newScope: (Int) -> S,
        private val action: ScopedIterativeAction<S>
    ) : AbstractTask<List<S>>() {
        val threadRanges = AtomicLongArray(workerCount + 1)
        private val scopes = List(workerCount) { newScope(it) }

        init {
            threadRanges.set(workerCount, size.toLong())
            var start = 0
            val step = maxOf(size / workerCount, batchSize)
            for (i in 0 until workerCount) {
                var end = minOf(start + step, size)
                // Last worker always absorbs all remaining items, regardless of how many are left.
                // Non-last workers also absorb the tail if it's smaller than one full batch.
                if (i == workerCount - 1 || size - end < batchSize) {
                    end = size
                }
                threadRanges.set(i, encodeRange(start, end))
                start = end
            }
        }

        private fun encodeRange(start: Int, end: Int): Long {
            return (end.toLong() shl 32) or Integer.toUnsignedLong(start)
        }

        private fun decodeRangeStart(encoded: Long): Int {
            return (encoded and 0xFFFFFFFFL).toInt()
        }

        private fun decodeRangeEnd(encoded: Long): Int {
            return (encoded ushr 32).toInt()
        }

        private fun steal(worker: Worker) {
            if (workerCountMinus1 <= 0) return
            Thread.yield()
            val victimIDBaseOffset = worker.random.nextInt(workerCountMinus1)

            for (i in 0..<workerCountMinus1) {
                val victimIDOffset = (victimIDBaseOffset + i) % (workerCountMinus1)
                val victimID = (worker.id + 1 + victimIDOffset) % workerCount
                val encoded = threadRanges.get(victimID)
                val start = decodeRangeStart(encoded)
                val end = decodeRangeEnd(encoded)
                val remaining = end - start
                if (remaining < batchSize * 2) continue
                val half = remaining / 2
                val newStart = start + half
                if (!threadRanges.compareAndSet(victimID, encoded, encodeRange(newStart, end))) continue

                threadRanges.set(worker.id, encodeRange(start, newStart))
                return
            }
        }

        private fun runBatch(worker: Worker, start: Int, end: Int) {
            with(action) {
                scopes[worker.id].run(start, end)
            }
        }

        override fun run(worker: Worker) {
            try {
                var completeCount = 0
                while (true) {
                    val encoded = threadRanges.get(worker.id)
                    val start = decodeRangeStart(encoded)
                    val end = decodeRangeEnd(encoded)
                    assert(start <= end)

                    if (start == end) {
                        val v = if (completeCount == 0) {
                            threadRanges.get(workerCount)
                        } else {
                            threadRanges.addAndGet(workerCount, -completeCount.toLong())
                        }
                        completeCount = 0
                        if (v < batchSize * 2 * workerCount) {
                            break
                        }
                        steal(worker)
                        continue
                    }

                    var newStart = minOf(start + batchSize, end)
                    if (end - newStart < batchSize) {
                        newStart = end
                    }
                    if (!threadRanges.compareAndSet(worker.id, encoded, encodeRange(newStart, end))) continue

                    runBatch(worker, start, newStart)
                    completeCount += newStart - start
                }

                if (threadRanges.get(workerCount) == 0L) {
                    complete(Result.success(scopes))
                }
            } catch (e: Throwable) {
                completeFailure(Result.failure(ExecutionException("Exception in worker ${worker.name}", e)))
            }
        }

        override fun submit() {
            checkState()
            workers.forEach {
                it.taskQueue.enqueueExclusive(this)
            }
        }

        override fun toString(): String {
            val sb = StringBuilder()
            sb.append("Total: ${threadRanges.get(workerCount)}/${size}\n")
            for (i in 0 until workerCount) {
                val encoded = threadRanges.get(i)
                val start = decodeRangeStart(encoded)
                val end = decodeRangeEnd(encoded)
                sb.append("Worker $i: $start-$end\n")
            }
            return sb.toString()
        }
    }

    private class IterativeTask(private val scoped: ScopedIterativeTask<Unit>) : Task<Unit> {
        override val isCompleted by scoped::isCompleted

        override suspend fun await(pollingTimeout: Long): Result<Unit> {
            return scoped.await(pollingTimeout).map { }
        }
    }

    sealed class TaskBuilder<T> {
        private var submitted = false

        fun submit(): Task<T> {
            val currentThread = Thread.currentThread()
            check(currentThread is Worker) { "Task must be submitted from worker thread" }
            return submit(currentThread.pool)
        }

        fun submit(pool: LWWSP): Task<T> {
            return synchronized(this) {
                check(!submitted) { "Task already submitted" }
                submitted = true
                submitImpl(pool)
            }
        }

        protected abstract fun submitImpl(pool: LWWSP): Task<T>
    }

    companion object {
        private class ScopedIterativeTaskBuilder<S>(
            private val size: Int,
            private val batchSize: Int,
            private val newScope: (workerID: Int) -> S,
            private val action: ScopedIterativeAction<S>
        ) : TaskBuilder<List<S>>() {
            override fun submitImpl(pool: LWWSP): Task<List<S>> {
                val task = pool.ScopedIterativeTask(size, batchSize, newScope, action)
                task.submit()
                return task
            }
        }

        private class IterativeTaskBuilder(
            private val size: Int,
            private val batchSize: Int,
            private val action: IterativeAction
        ) : TaskBuilder<Unit>() {
            override fun submitImpl(pool: LWWSP): Task<Unit> {
                val task = pool.ScopedIterativeTask(size, batchSize, { }, { start, end -> action.run(start, end) })
                task.submit()
                return IterativeTask(task)
            }
        }

        private class RecursiveTaskBuilder<R>(
            private val action: RecursiveAction<R>
        ) : TaskBuilder<R>() {
            override fun submitImpl(pool: LWWSP): Task<R> {
                val task = pool.RecursiveTask(action)
                task.submit()
                return task
            }
        }

        inline fun <S> iterativeTask(
            size: Int,
            batchSize: Int,
            crossinline newScope: () -> S,
            action: ScopedIterativeAction<S>
        ): TaskBuilder<List<S>> {
            return iterativeTask(size, batchSize, { workerID: Int -> newScope() }, action)
        }

        fun <S> iterativeTask(
            size: Int,
            batchSize: Int,
            newScopeByWorkerID: (workerID: Int) -> S,
            action: ScopedIterativeAction<S>
        ): TaskBuilder<List<S>> {
            return ScopedIterativeTaskBuilder(size, batchSize, newScopeByWorkerID, action)
        }

        fun iterativeTask(
            size: Int,
            batchSize: Int,
            action: IterativeAction
        ): TaskBuilder<Unit> {
            return IterativeTaskBuilder(size, batchSize, action)
        }
    }
}