package net.spartanb312.grunteon.backend

import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

@Service
class JobService(
    properties: BackendProperties,
    private val repository: RedisJobRepository,
    private val queue: RedisJobQueue,
) {
    private val workDir = properties.workDir.toAbsolutePath().normalize()

    init {
        workDir.createDirectories()
    }

    fun submit(configFile: MultipartFile, inputFile: MultipartFile, libs: List<MultipartFile>): JobStatusResponse {
        require(!configFile.isEmpty) { "config file is required" }
        require(!inputFile.isEmpty) { "input jar is required" }

        val jobId = UUID.randomUUID().toString()
        val jobDir = workDir.resolve(jobId).normalize()
        require(jobDir.startsWith(workDir)) { "Invalid job directory" }
        jobDir.createDirectories()

        val configPath = jobDir.resolve("config.json")
        val inputPath = jobDir.resolve("input.jar")
        val libsDir = jobDir.resolve("libs").also { it.createDirectories() }

        saveConfig(configFile, configPath)
        inputFile.transferTo(inputPath)

        val libPaths = libs
            .filterNot { it.isEmpty }
            .mapIndexed { index, file ->
                val safeName = file.originalFilename
                    ?.substringAfterLast('/')
                    ?.substringAfterLast('\\')
                    ?.takeIf { it.endsWith(".jar", ignoreCase = true) }
                    ?: "lib-$index.jar"
                libsDir.resolve(safeName).also { file.transferTo(it) }
            }

        val now = Instant.now()
        val metadata = JobMetadata(
            id = jobId,
            dir = jobDir.toString(),
            status = JobStatus.QUEUED,
            createdAt = now,
            updatedAt = now,
        )
        repository.create(metadata)
        queue.enqueue(jobId)

        return metadata.toResponse(resultAvailable = false)
    }

    fun get(jobId: String): JobStatusResponse {
        val metadata = find(jobId)
        return metadata.toResponse(resultAvailable = resultZip(metadata).exists())
    }

    fun resultPath(jobId: String): Path {
        val metadata = find(jobId)
        val resultZip = resultZip(metadata)
        require(metadata.status == JobStatus.SUCCESS) { "Job $jobId has not completed successfully" }
        require(resultZip.exists()) { "Result for job $jobId is not available" }
        return resultZip
    }

    private fun saveConfig(configFile: MultipartFile, configPath: Path) {
        val bytes = configFile.bytes
        val offset = if (
            bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            3
        } else {
            0
        }
        Files.write(configPath, bytes.copyOfRange(offset, bytes.size))
    }

    private fun find(jobId: String): JobMetadata {
        return repository.find(jobId) ?: throw NoSuchElementException("Job $jobId was not found")
    }

    private fun resultZip(metadata: JobMetadata): Path {
        return Path.of(metadata.dir).resolve("result.zip")
    }
}
