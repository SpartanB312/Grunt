package net.spartanb312.grunteon.backend

import org.springframework.core.io.InputStreamResource
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.nio.file.NoSuchFileException

@RestController
@RequestMapping("/api/jobs")
class JobController(
    private val jobService: JobService,
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun submit(
        @RequestPart("config") config: MultipartFile,
        @RequestPart("input") input: MultipartFile,
        @RequestPart("libs", required = false) libs: List<MultipartFile>?,
    ): JobSubmitResponse {
        val response = jobService.submit(config, input, libs.orEmpty())
        return JobSubmitResponse(response.jobId, response.status)
    }

    @GetMapping("/{jobId}")
    fun status(@PathVariable jobId: String): JobStatusResponse {
        return jobService.get(jobId)
    }

    @GetMapping("/{jobId}/result")
    fun result(@PathVariable jobId: String): ResponseEntity<InputStreamResource> {
        val result = jobService.openResult(jobId)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("result.zip").build().toString(),
            )
            .body(InputStreamResource(result))
    }

    @ExceptionHandler(JobAdmissionException::class)
    fun overloaded(error: JobAdmissionException): ResponseEntity<ErrorResponse> = ResponseEntity.status(429)
        .header(HttpHeaders.RETRY_AFTER, "30").body(ErrorResponse(error.message ?: "Job quota is full"))

    @ExceptionHandler(ConfigTooLargeException::class)
    fun configTooLarge(error: ConfigTooLargeException): ResponseEntity<ErrorResponse> = ResponseEntity.status(413)
        .body(ErrorResponse(error.message ?: "Config is too large"))

    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
    fun badRequest(error: RuntimeException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest().body(ErrorResponse(error.message ?: "Bad request"))
    }

    @ExceptionHandler(NoSuchElementException::class, NoSuchFileException::class)
    fun notFound(error: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(404).body(ErrorResponse(error.message ?: "Not found"))
    }

    @ExceptionHandler(RedisConnectionFailureException::class)
    fun redisUnavailable(error: RedisConnectionFailureException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(503).header(HttpHeaders.RETRY_AFTER, "5")
            .body(ErrorResponse(error.message ?: "Redis is unavailable"))
    }
}
