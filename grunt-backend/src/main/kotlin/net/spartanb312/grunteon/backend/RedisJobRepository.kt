package net.spartanb312.grunteon.backend

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class RedisJobRepository(
    private val redis: StringRedisTemplate,
    private val properties: BackendProperties,
) {
    private val hashOps = redis.opsForHash<String, String>()

    fun create(metadata: JobMetadata) {
        hashOps.putAll(metadata.key(), metadata.toMap())
    }

    fun find(jobId: String): JobMetadata? {
        val values = hashOps.entries(key(jobId))
        if (values.isEmpty()) return null
        return values.toMetadata()
    }

    fun updateStatus(jobId: String, status: JobStatus, error: String? = null) {
        val now = Instant.now().toString()
        val key = key(jobId)
        hashOps.put(key, "status", status.name)
        hashOps.put(key, "updatedAt", now)
        if (error != null) {
            hashOps.put(key, "error", error)
        } else {
            hashOps.delete(key, "error")
        }
    }

    private fun JobMetadata.key(): String = key(id)

    private fun key(jobId: String): String = properties.jobKeyPrefix + jobId

    private fun JobMetadata.toMap(): Map<String, String> {
        return buildMap {
            put("id", id)
            put("dir", dir)
            put("status", status.name)
            put("createdAt", createdAt.toString())
            put("updatedAt", updatedAt.toString())
            error?.let { put("error", it) }
        }
    }

    private fun Map<String, String>.toMetadata(): JobMetadata {
        return JobMetadata(
            id = requireNotNull(get("id")) { "Missing job id" },
            dir = requireNotNull(get("dir")) { "Missing job dir" },
            status = JobStatus.valueOf(requireNotNull(get("status")) { "Missing job status" }),
            createdAt = Instant.parse(requireNotNull(get("createdAt")) { "Missing createdAt" }),
            updatedAt = Instant.parse(requireNotNull(get("updatedAt")) { "Missing updatedAt" }),
            error = get("error"),
        )
    }
}
