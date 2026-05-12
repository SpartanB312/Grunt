package net.spartanb312.grunteon.backend

import jakarta.annotation.PreDestroy
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Component
class JobDispatcher(
    private val properties: BackendProperties,
    private val queue: RedisJobQueue,
    private val repository: RedisJobRepository,
    private val workerProcessRunner: WorkerProcessRunner,
) : ApplicationRunner {
    private val running = AtomicBoolean(false)
    private lateinit var executor: ExecutorService

    override fun run(args: ApplicationArguments) {
        val concurrency = properties.workerConcurrency.coerceAtLeast(1)
        executor = Executors.newFixedThreadPool(concurrency) { task ->
            Thread(task, "grunt-dispatcher-worker").apply { isDaemon = true }
        }
        running.set(true)
        repeat(concurrency) { index ->
            executor.submit { dispatchLoop(index) }
        }
    }

    @PreDestroy
    fun stop() {
        running.set(false)
        if (::executor.isInitialized) executor.shutdownNow()
    }

    private fun dispatchLoop(index: Int) {
        while (running.get()) {
            val jobId = runCatching { queue.poll(Duration.ofSeconds(2)) }
                .getOrElse {
                    Thread.sleep(1_000L)
                    null
                } ?: continue
            runJob(jobId, index)
        }
    }

    private fun runJob(jobId: String, workerIndex: Int) {
        val metadata = repository.find(jobId) ?: return
        repository.updateStatus(jobId, JobStatus.RUNNING)
        val exitCode = runCatching {
            workerProcessRunner.run(Path.of(metadata.dir))
        }.getOrElse { error ->
            repository.updateStatus(jobId, JobStatus.FAILED, error.message ?: error::class.qualifiedName)
            return
        }
        if (exitCode == 0) {
            repository.updateStatus(jobId, JobStatus.SUCCESS)
        } else {
            repository.updateStatus(jobId, JobStatus.FAILED, "Worker $workerIndex exited with code $exitCode")
        }
    }
}
