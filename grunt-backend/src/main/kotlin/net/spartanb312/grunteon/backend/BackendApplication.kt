package net.spartanb312.grunteon.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import java.nio.file.Path
import kotlin.io.path.Path

@SpringBootApplication
@EnableConfigurationProperties(BackendProperties::class)
class BackendApplication

fun main(args: Array<String>) {
    if (args.firstOrNull() == "worker") {
        require(args.size in 2..3) { "Usage: grunt-backend worker <job-dir> [attempt-id]" }
        WorkerJobRunner.run(Path(args[1]), args.getOrNull(2))
        return
    }
    runApplication<BackendApplication>(*args)
}

@ConfigurationProperties("grunteon.backend")
class BackendProperties {
    var workDir: Path = Path("work/backend-jobs")
    var queueKey: String = "grunteon:jobs:queue"
    var jobKeyPrefix: String = "grunteon:jobs:"
    var workerConcurrency: Int = 2
    var javaExecutable: String = ""
    var workerJavaOptions: List<String> = emptyList()
    var workerTimeoutSeconds: Long = 0
    var workerHeapMiB: Int = 4096
    var workerOverheadMiB: Int = 256
    var nativeCompileMemoryMiB: Int = 256
    var nativeCompileJobs: Int = 1
    var workerCpuCount: Int = 0
    var totalWorkerMemoryMiB: Int = 9216
    var totalWorkerCpuCount: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    var workerTerminationGraceSeconds: Long = 5
    var workerReapTimeoutSeconds: Long = 5
    var claimLeaseSeconds: Long = 60
    var heartbeatSeconds: Long = 10
    var maxAdmittedJobs: Int = 64
    var maxStoredUploadBytes: Long = 8L * 1024 * 1024 * 1024
    var minFreeDiskBytes: Long = 1024L * 1024 * 1024
    var maxConfigBytes: Long = 1024L * 1024
    // Disabled by default: upgrading the backend must not delete historical results.
    var retentionSeconds: Long = 0
    var abandonedUploadSeconds: Long = 3600
    var cleanupIntervalSeconds: Long = 60
    var cleanupBatchSize: Int = 16

    // Existing -Xmx/-XX heap and CPU options retain their meaning and take precedence over the new settings.
    internal fun legacyHeapOption(): String? = workerJavaOptions.lastOrNull {
        it.startsWith("-Xmx") || it.startsWith("-XX:MaxHeapSize=")
    }
    internal fun legacyCpuOption(): String? = workerJavaOptions.lastOrNull { it.startsWith("-XX:ActiveProcessorCount=") }

    fun effectiveWorkerHeapMiB(): Long {
        val option = legacyHeapOption() ?: return workerHeapMiB.toLong()
        val size = option.removePrefix("-Xmx").removePrefix("-XX:MaxHeapSize=")
        val match = requireNotNull(Regex("([0-9]+)([kKmMgGtT]?)").matchEntire(size)) { "Invalid worker heap option: $option" }
        val multiplier = when (match.groupValues[2].lowercase()) {
            "k" -> 1024L; "m" -> 1024L * 1024; "g" -> 1024L * 1024 * 1024; "t" -> 1024L * 1024 * 1024 * 1024
            else -> 1L
        }
        val bytes = Math.multiplyExact(match.groupValues[1].toLong(), multiplier)
        require(bytes > 0) { "Worker heap must be positive" }
        return (bytes - 1) / (1024 * 1024) + 1
    }

    private fun memoryWorkerCount(): Int {
        val memoryPerWorker = effectiveWorkerHeapMiB() + workerOverheadMiB +
                nativeCompileMemoryMiB.toLong() * nativeCompileJobs
        return minOf(workerConcurrency.toLong(), totalWorkerMemoryMiB / memoryPerWorker).toInt()
    }

    fun effectiveWorkerCpuCount(): Int {
        legacyCpuOption()?.let {
            val requested = it.substringAfter('=').toInt()
            return if (requested > 0) requested else Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        }
        return if (workerCpuCount > 0) workerCpuCount
            else (totalWorkerCpuCount / memoryWorkerCount().coerceAtLeast(1)).coerceAtLeast(1)
    }

    fun effectiveWorkerConcurrency(): Int {
        require(workerConcurrency > 0 && workerHeapMiB > 0 && workerOverheadMiB >= 0)
        require(nativeCompileMemoryMiB > 0 && nativeCompileJobs > 0 && workerCpuCount >= 0)
        require(totalWorkerMemoryMiB > 0 && totalWorkerCpuCount > 0)
        require(listOf(workerTimeoutSeconds, workerTerminationGraceSeconds, workerReapTimeoutSeconds,
            claimLeaseSeconds, heartbeatSeconds, retentionSeconds, abandonedUploadSeconds, cleanupIntervalSeconds)
            .all { it in 0..Long.MAX_VALUE / 1000 })
        require(workerReapTimeoutSeconds > 0 && heartbeatSeconds > 0 && claimLeaseSeconds >= heartbeatSeconds * 3)
        require(maxAdmittedJobs > 0 && maxStoredUploadBytes > 0 && maxConfigBytes > 0 && minFreeDiskBytes >= 0)
        require(retentionSeconds >= 0 && abandonedUploadSeconds > 0 && cleanupIntervalSeconds > 0 && cleanupBatchSize > 0)
        val cpuPerWorker = maxOf(effectiveWorkerCpuCount(), nativeCompileJobs)
        val count = minOf(memoryWorkerCount(), totalWorkerCpuCount / cpuPerWorker)
        require(count > 0) { "Worker CPU/memory budgets cannot admit one worker" }
        return count
    }
}
