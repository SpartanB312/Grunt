package net.spartanb312.grunteon.backend

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Component
class JobDispatcher(
    private val properties: BackendProperties,
    private val queue: RedisJobQueue,
    private val repository: RedisJobRepository,
    private val workerProcessRunner: WorkerProcessRunner,
    private val files: JobFiles,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)
    val liveLoops = AtomicInteger()
    val quarantinedSlots = AtomicInteger()
    private lateinit var executor: ExecutorService

    override fun run(args: ApplicationArguments) {
        val concurrency = properties.effectiveWorkerConcurrency()
        executor = Executors.newFixedThreadPool(concurrency) { task ->
            Thread(task, "grunt-dispatcher-worker").apply { isDaemon = true }
        }
        running.set(true)
        repeat(concurrency) { executor.submit { dispatchLoop() } }
        logger.info("Started {} isolated workers (heap={} MiB, CPU={} each)", concurrency,
            properties.effectiveWorkerHeapMiB(), properties.effectiveWorkerCpuCount())
    }

    @PreDestroy
    fun stop() {
        running.set(false)
        if (::executor.isInitialized) {
            executor.shutdownNow()
            // Child termination/reaping runs in finally, even when shutdown interrupts waitFor.
            executor.awaitTermination(properties.workerTerminationGraceSeconds + properties.workerReapTimeoutSeconds + 5, TimeUnit.SECONDS)
        }
    }

    private fun dispatchLoop() {
        liveLoops.incrementAndGet()
        try {
            supervisedLoop(running::get, { delay -> Thread.sleep(delay) }, { error ->
                logger.warn("Dispatch iteration failed; claim will recover after its lease expires", error)
            }) {
                val claim = queue.claim()
                if (claim == null) queue.awaitWork() else runJob(claim)
                true
            }
        } catch (error: UnreapedWorkerException) {
            quarantinedSlots.incrementAndGet()
            logger.error("Worker slot quarantined; operator must verify/terminate remaining processes before restart", error)
        } finally { liveLoops.decrementAndGet() }
    }

    internal fun runJob(claim: JobClaim) {
        val metadata = repository.find(claim.jobId) ?: return
        val dir = files.directory(metadata)
        // Only the claimed attempt can commit its result. Stale attempts never overwrite a published archive.
        val resultFile = "attempts/${claim.token}/result.zip"
        var failure: String? = null
        val exit = try {
            workerProcessRunner.run(dir, claim.token) { check(queue.renew(claim)) { "Worker claim lease was lost" } }
        } catch (_: WorkerAttemptNotStartedException) {
            // A recovered attempt may still be waiting for a stale JVM to release its file lock.
            // The process is already reaped; leave the claim to expire/retry, never mark the job FAILED for contention.
            return
        } catch (unreaped: UnreapedWorkerException) {
            throw unreaped
        } catch (interrupted: InterruptedException) {
            throw interrupted
        } catch (error: Exception) {
            failure = error.message ?: error.javaClass.name
            -1
        }
        val available = exit == 0 && Files.isRegularFile(dir.resolve(resultFile))
        val status = if (available) JobStatus.SUCCESS else JobStatus.FAILED
        // This also atomically acknowledges/removes the processing lease. A failed write is recovered, not lost.
        queue.complete(claim, status, if (available) null else failure ?: "Worker exited with code $exit",
            if (available) resultFile else "")
    }
}

/** Supervise the entire claim/find/run/commit iteration, not just queue I/O. */
internal fun supervisedLoop(running: () -> Boolean, pause: (Long) -> Unit, failed: (Exception) -> Unit,
                            iteration: () -> Boolean) {
    var delay = 250L
    while (running() && !Thread.currentThread().isInterrupted) {
        try {
            if (iteration()) delay = 250L
            else {
                pause(delay)
                delay = (delay * 2).coerceAtMost(2000L)
            }
        } catch (unreaped: UnreapedWorkerException) {
            throw unreaped
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return
        } catch (error: Exception) {
            failed(error)
            try { pause(delay) } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
            delay = (delay * 2).coerceAtMost(10000L)
        }
    }
}
