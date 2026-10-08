package net.spartanb312.grunteon.backend

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
class JobRetention(
    private val properties: BackendProperties,
    private val queue: RedisJobQueue,
    private val repository: RedisJobRepository,
    private val files: JobFiles,
    private val service: JobService,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val offsets = longArrayOf(0, 0)
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "grunt-job-retention").apply { isDaemon = true }
    }

    override fun run(args: ApplicationArguments) {
        executor.scheduleWithFixedDelay({
            try { cleanup() } catch (error: Exception) { logger.warn("Job cleanup will retry", error) }
        }, properties.cleanupIntervalSeconds, properties.cleanupIntervalSeconds, TimeUnit.SECONDS)
    }

    internal fun cleanup() {
        for (id in candidates(uploads = true)) {
            try { service.cleanupUpload(id, properties.abandonedUploadSeconds) } catch (error: Exception) { logger.warn("Upload cleanup failed for {}", id, error) }
        }
        if (properties.retentionSeconds <= 0) return
        for (id in candidates(uploads = false)) {
            try {
                val metadata = repository.find(id) ?: continue
                files.directory(metadata) // Never remove paths belonging to a different storage root.
                if (files.stageRemoval(id, upload = false) { queue.beginDelete(id) }) {
                    files.removeStaged(id)
                    queue.finishDelete(id)
                }
            } catch (error: Exception) { logger.warn("Terminal cleanup failed for {}", id, error) }
        }
    }

    private fun candidates(uploads: Boolean): Set<String> {
        val index = if (uploads) 0 else 1
        val batch = queue.cleanupCandidates(uploads, offsets[index])
        // Rotate past long-lived download/upload leases instead of starving later eligible jobs.
        offsets[index] = if (batch.size < properties.cleanupBatchSize) 0 else offsets[index] + batch.size
        return batch
    }

    @PreDestroy
    fun stop() { executor.shutdownNow() }
}
