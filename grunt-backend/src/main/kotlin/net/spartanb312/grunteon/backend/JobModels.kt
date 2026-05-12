package net.spartanb312.grunteon.backend

import java.time.Instant

enum class JobStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
}

data class JobSubmitResponse(
    val jobId: String,
    val status: JobStatus,
)

data class JobStatusResponse(
    val jobId: String,
    val status: JobStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val error: String?,
    val resultAvailable: Boolean,
)

data class JobMetadata(
    val id: String,
    val dir: String,
    val status: JobStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val error: String? = null,
) {
    fun toResponse(resultAvailable: Boolean): JobStatusResponse {
        return JobStatusResponse(
            jobId = id,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
            error = error,
            resultAvailable = resultAvailable,
        )
    }
}

data class ErrorResponse(
    val message: String,
)
