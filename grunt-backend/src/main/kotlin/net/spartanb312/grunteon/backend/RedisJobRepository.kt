package net.spartanb312.grunteon.backend

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class RedisJobRepository(
    redis: StringRedisTemplate,
    private val properties: BackendProperties,
) {
    private val hashOps = redis.opsForHash<String, String>()

    fun find(jobId: String): JobMetadata? {
        val values = hashOps.entries(properties.jobKeyPrefix + jobId)
        if (values.isEmpty()) return null
        return JobMetadata(
            id = requireNotNull(values["id"]) { "Missing job id" },
            dir = requireNotNull(values["dir"]) { "Missing job dir" },
            status = JobStatus.valueOf(requireNotNull(values["status"]) { "Missing job status" }),
            createdAt = Instant.parse(requireNotNull(values["createdAt"]) { "Missing createdAt" }),
            updatedAt = Instant.parse(requireNotNull(values["updatedAt"]) { "Missing updatedAt" }),
            error = values["error"],
            // Old successful jobs predate the availability field; download still validates the file.
            resultAvailable = values["resultAvailable"]?.let { it == "1" } ?: (values["status"] == "SUCCESS"),
            resultFile = values["resultFile"]?.takeIf { it.isNotBlank() } ?: "result.zip",
            deleting = values["deleting"] == "1",
        )
    }
}
