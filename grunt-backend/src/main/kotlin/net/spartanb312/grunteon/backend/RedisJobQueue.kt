package net.spartanb312.grunteon.backend

import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

internal data class JobClaim(val jobId: String, val token: String)

@Component
class RedisJobQueue(
    private val redis: StringRedisTemplate,
    private val properties: BackendProperties,
) {
    private val script = DefaultRedisScript<String>().apply {
        setLocation(ClassPathResource("redis/jobs.lua"))
        resultType = String::class.java
    }

    private fun execute(operation: String, id: String = "", vararg args: String): String? {
        val base = properties.queueKey
        return redis.execute(script, listOf(base, "$base:leases", "$base:active", "$base:upload-bytes",
            "$base:total-bytes", "$base:staged", "$base:terminal", properties.jobKeyPrefix + id,
            "$base:cancelled-uploads", "$base:wakeups"), operation, properties.jobKeyPrefix, *args)
    }

    fun reserve(jobId: String, bytes: Long): Boolean = execute("reserve", jobId, jobId, bytes.toString(),
        properties.maxAdmittedJobs.toString(), properties.maxStoredUploadBytes.toString()) == "1"

    fun createAndEnqueue(metadata: JobMetadata) {
        check(execute("create", metadata.id, metadata.id, metadata.dir, metadata.createdAt.toString(),
            properties.workerConcurrency.toString()) == "1") {
            "Upload reservation expired"
        }
    }

    fun cancelUpload(jobId: String, minAgeSeconds: Long = 0): Boolean =
        execute("cancelUpload", jobId, jobId, (minAgeSeconds * 1000).toString()) == "1"
    fun releaseUpload(jobId: String) { execute("releaseUpload", jobId, jobId) }

    internal fun awaitWork() {
        // A lossy/coalesced wakeup is only a hint: the authoritative queue is popped solely by the claim script.
        redis.opsForList().leftPop(properties.queueKey + ":wakeups", Duration.ofSeconds(2))
    }

    internal fun claim(): JobClaim? {
        val token = UUID.randomUUID().toString()
        val id = execute("claim", "", token, (properties.claimLeaseSeconds * 1000).toString(),
            Instant.now().toString(), properties.cleanupBatchSize.toString())
        return id?.takeIf { it.isNotBlank() }?.let { JobClaim(it, token) }
    }

    internal fun renew(claim: JobClaim): Boolean = execute("renew", claim.jobId, claim.jobId, claim.token,
        (properties.claimLeaseSeconds * 1000).toString()) == "1"

    internal fun complete(claim: JobClaim, status: JobStatus, error: String?, resultFile: String = ""): Boolean {
        require(status == JobStatus.SUCCESS || status == JobStatus.FAILED)
        return execute("complete", claim.jobId, claim.jobId, claim.token, status.name,
            Instant.now().toString(), error.orEmpty(), resultFile) == "1"
    }

    fun cleanupCandidates(uploads: Boolean, offset: Long = 0): Set<String> {
        val age = if (uploads) properties.abandonedUploadSeconds else properties.retentionSeconds
        if (age <= 0) return emptySet()
        val key = properties.queueKey + if (uploads) ":staged" else ":terminal"
        return redis.opsForZSet().rangeByScore(key, 0.0, (System.currentTimeMillis() - age * 1000).toDouble(),
            offset, properties.cleanupBatchSize.toLong()).orEmpty()
    }

    fun beginDelete(jobId: String): Boolean = properties.retentionSeconds > 0 &&
            execute("beginDelete", jobId, jobId, (properties.retentionSeconds * 1000).toString()) == "1"

    fun finishDelete(jobId: String) { execute("finishDelete", jobId, jobId) }
}
