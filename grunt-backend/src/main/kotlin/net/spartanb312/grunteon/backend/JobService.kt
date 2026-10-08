package net.spartanb312.grunteon.backend

import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.time.Instant
import java.util.UUID

class JobAdmissionException(message: String) : RuntimeException(message)
class ConfigTooLargeException(message: String) : IllegalArgumentException(message)

@Service
class JobService(
    private val properties: BackendProperties,
    private val repository: RedisJobRepository,
    private val queue: RedisJobQueue,
    private val files: JobFiles,
) {
    fun submit(configFile: MultipartFile, inputFile: MultipartFile, libs: List<MultipartFile>): JobStatusResponse {
        require(!configFile.isEmpty) { "config file is required" }
        require(!inputFile.isEmpty) { "input jar is required" }
        if (configFile.size > properties.maxConfigBytes) throw ConfigTooLargeException("Config exceeds configured size limit")
        val bytes = (listOf(configFile, inputFile) + libs).fold(0L) { total, file -> Math.addExact(total, file.size) }
        if (Files.getFileStore(files.root).usableSpace - properties.minFreeDiskBytes < bytes) {
            throw JobAdmissionException("Insufficient free disk space; retry after capacity is available")
        }
        val jobId = UUID.randomUUID().toString()
        if (!queue.reserve(jobId, bytes)) throw JobAdmissionException("Job queue or storage quota is full")
        val staging = files.staging(jobId)
        val jobDir = files.directory(jobId)
        val now = Instant.now()
        val metadata = JobMetadata(jobId, jobDir.toString(), JobStatus.QUEUED, now, now)
        try {
            Files.createDirectories(staging)
            requireNotNull(exclusiveLease(staging, ".worker.lock", wait = true)).use {
                configFile.inputStream.use { input ->
                    Files.newOutputStream(staging.resolve("config.json")).use { output ->
                        copyConfig(input, output, properties.maxConfigBytes)
                    }
                }
                inputFile.transferTo(staging.resolve("input.jar"))
                val libsDir = Files.createDirectories(staging.resolve("libs"))
                libs.filterNot { it.isEmpty }.forEachIndexed { index, file ->
                    val safeName = file.originalFilename?.substringAfterLast('/')?.substringAfterLast('\\')
                        ?.takeIf { it.endsWith(".jar", ignoreCase = true) } ?: "lib-$index.jar"
                    val destination = libsDir.resolve(safeName).normalize()
                    require(destination.startsWith(libsDir)) { "Invalid library name" }
                    file.transferTo(destination)
                }
                Files.move(staging, jobDir, ATOMIC_MOVE)
                queue.createAndEnqueue(metadata)
            }
            return metadata.toResponse(resultAvailable = false)
        } catch (error: Exception) {
            // The Redis write may have succeeded despite a lost reply. Never delete an accepted job.
            runCatching { cleanupUpload(jobId) }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    internal fun cleanupUpload(jobId: String, minAgeSeconds: Long = 0) {
        if (files.stageRemoval(jobId, upload = true) { queue.cancelUpload(jobId, minAgeSeconds) }) {
            files.removeStaged(jobId)
            queue.releaseUpload(jobId)
        }
    }

    fun get(jobId: String): JobStatusResponse {
        val metadata = find(jobId)
        return metadata.toResponse(resultAvailable = metadata.resultAvailable && !metadata.deleting)
    }

    fun openResult(jobId: String): InputStream = files.openResult(find(jobId))

    fun resultPath(jobId: String): Path {
        val metadata = find(jobId)
        require(metadata.status == JobStatus.SUCCESS && !metadata.deleting) { "Job has not completed successfully" }
        return files.result(metadata).also { require(Files.isRegularFile(it)) { "Result is not available" } }
    }

    private fun find(jobId: String): JobMetadata {
        try { files.directory(jobId) } catch (_: IllegalArgumentException) {
            throw NoSuchElementException("Job $jobId was not found")
        }
        return repository.find(jobId) ?: throw NoSuchElementException("Job $jobId was not found")
    }
}

/** Strip only a complete UTF-8 BOM, preserving partial prefixes and all JSON bytes. Limit includes the BOM. */
internal fun copyConfig(input: InputStream, output: OutputStream, maxBytes: Long) {
    require(maxBytes > 0)
    val prefix = input.readNBytes(3)
    var total = prefix.size.toLong()
    if (total > maxBytes) throw ConfigTooLargeException("Config exceeds configured size limit")
    if (!prefix.contentEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))) output.write(prefix)
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) return
        total += count
        if (total > maxBytes) throw ConfigTooLargeException("Config exceeds configured size limit")
        output.write(buffer, 0, count)
    }
}
