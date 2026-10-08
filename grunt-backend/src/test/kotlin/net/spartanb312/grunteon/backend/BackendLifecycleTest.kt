package net.spartanb312.grunteon.backend

import org.mockito.Mockito
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.core.io.ClassPathResource
import org.springframework.mock.web.MockMultipartFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.test.*

class BackendLifecycleTest {
    @Test
    fun yamlUsesEffectiveMultipartNamespaceAndBudgetsBind() {
        val yaml = YamlPropertiesFactoryBean().apply { setResources(ClassPathResource("application.yml")) }
        val values = requireNotNull(yaml.getObject()).entries.associate { it.key.toString() to it.value }
        assertEquals("512MB", values["spring.servlet.multipart.max-file-size"])
        assertEquals("2048MB", values["spring.servlet.multipart.max-request-size"])
        val source = MapConfigurationPropertySource(values + ("grunteon.backend.worker-heap-mi-b" to "768"))
        val properties = Binder(source).bind("grunteon.backend", Bindable.of(BackendProperties::class.java)).get()
        assertEquals(768, properties.workerHeapMiB)
        assertEquals(9216, properties.totalWorkerMemoryMiB)
        assertEquals(0L, properties.retentionSeconds)
    }

    @Test
    fun configStreamingPreservesBytesAndStripsOnlyCompleteBom() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        for (bytes in listOf(byteArrayOf(), byteArrayOf(0xEF.toByte()), bom.copyOf(2), "{}".toByteArray(),
            bom + "{\"seed\":123}".toByteArray(), ByteArray(25000) { (it % 251).toByte() })) {
            val output = ByteArrayOutputStream()
            copyConfig(ByteArrayInputStream(bytes), output, 30000)
            val expected = if (bytes.take(3).toByteArray().contentEquals(bom)) bytes.drop(3).toByteArray() else bytes
            assertContentEquals(expected, output.toByteArray())
        }
    }

    @Test
    fun configLimitChecksActualStreamIncludingBom() {
        assertFailsWith<ConfigTooLargeException> {
            copyConfig(ByteArrayInputStream(ByteArray(50000)), ByteArrayOutputStream(), 10000)
        }
        assertFailsWith<ConfigTooLargeException> {
            copyConfig(ByteArrayInputStream(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())),
                ByteArrayOutputStream(), 2)
        }
    }

    @Test
    fun budgetsAccountForHeapOverheadNativeAndCpu() {
        val p = BackendProperties().apply { workerConcurrency = 8; totalWorkerCpuCount = 8 }
        assertEquals(2, p.effectiveWorkerConcurrency())
        p.totalWorkerCpuCount = 1
        assertEquals(1, p.effectiveWorkerConcurrency())
        p.totalWorkerMemoryMiB = 100
        assertFailsWith<IllegalArgumentException> { p.effectiveWorkerConcurrency() }
        assertEquals(0L, BackendProperties().retentionSeconds)
    }

    @Test
    fun childCommandUsesExplicitCpuAndHeapBudgets() {
        val properties = BackendProperties().apply { totalWorkerCpuCount = 8 }
        val command = WorkerProcessRunner(properties).buildCommand(Path.of("job"), "attempt")
        assertTrue("-Xmx4096m" in command)
        assertTrue("-XX:ActiveProcessorCount=4" in command)
        assertTrue("-Dgrunteon.worker.nativeCompileJobs=1" in command)
        assertEquals(listOf("worker", "job", "attempt"), command.takeLast(3))
        properties.workerJavaOptions = listOf("-Xmx4G")
        val legacy = WorkerProcessRunner(properties).buildCommand(Path.of("job"), "attempt")
        assertEquals(4096L, properties.effectiveWorkerHeapMiB())
        assertEquals(2, properties.effectiveWorkerConcurrency())
        assertEquals(listOf("-Xmx4G"), legacy.filter { it.startsWith("-Xmx") })
    }

    @Test
    fun legacyCustomHeapAndCpuOptionsRetainMeaningAndDriveAdmission() {
        val properties = BackendProperties().apply {
            totalWorkerCpuCount = 8
            workerConcurrency = 4
            workerJavaOptions = listOf("-Xms64m", "-Xmx2G", "-XX:ActiveProcessorCount=2")
        }
        assertEquals(2048L, properties.effectiveWorkerHeapMiB())
        assertEquals(2, properties.effectiveWorkerCpuCount())
        assertEquals(3, properties.effectiveWorkerConcurrency()) // 9216 / (2048 + 256 + 256)
        val command = WorkerProcessRunner(properties).buildCommand(Path.of("job"), "attempt")
        assertEquals(listOf("-Xmx2G"), command.filter { it.startsWith("-Xmx") })
        assertEquals(listOf("-XX:ActiveProcessorCount=2"), command.filter { it.startsWith("-XX:ActiveProcessorCount=") })
        assertTrue("-Xms64m" in command)
        properties.workerJavaOptions = listOf("-Xmx1G", "-XX:MaxHeapSize=1536m")
        assertEquals(1536L, properties.effectiveWorkerHeapMiB())
        properties.workerJavaOptions = listOf("-Xmx1048577")
        assertEquals(2L, properties.effectiveWorkerHeapMiB()) // ceil byte-level options for conservative accounting
    }

    @Test
    fun unconfirmedTerminationIsBoundedAndCannotReuseSlot() {
        val process = Mockito.mock(Process::class.java)
        Mockito.`when`(process.isAlive).thenReturn(true)
        Mockito.`when`(process.descendants()).thenAnswer { java.util.stream.Stream.empty<ProcessHandle>() }
        Mockito.`when`(process.destroyForcibly()).thenReturn(process)
        assertFailsWith<UnreapedWorkerException> { terminateAndReap(process, reapTimeoutSeconds = 0) }
        Mockito.verify(process, Mockito.never()).waitFor() // No unbounded reap, even for a stuck OS process.
        var iterations = 0
        assertFailsWith<UnreapedWorkerException> {
            supervisedLoop({ true }, { fail("quarantined slot must not retry") }, { fail("must not swallow quarantine") }) {
                iterations++
                throw UnreapedWorkerException("D-state / unconfirmed orphan")
            }
        }
        assertEquals(1, iterations)
    }

    @Test
    fun supervisionCoversEveryIterationStageAndBoundsBackoff() {
        var iterations = 0
        var failures = 0
        val delays = mutableListOf<Long>()
        supervisedLoop({ iterations < 20 }, { delays += it }, { failures++ }) {
            iterations++
            if (iterations < 20) throw IllegalStateException("find / status / commit fault")
            true
        }
        assertEquals(20, iterations)
        assertEquals(19, failures)
        assertTrue(delays.all { it in 250..10000 })
        assertEquals(10000L, delays.last())
    }

    @Test
    fun supervisionPreservesInterruptAndStops() {
        try {
            supervisedLoop({ true }, { throw InterruptedException() }, { fail("unexpected failure") }) { false }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
    }

    @Test
    fun multipleDownloadLeasesBlockCleanupUntilLastClose() = withFiles { files, metadata ->
        val result = files.directory(metadata).resolve("result.zip")
        Files.writeString(result, "complete")
        val first = files.openResult(metadata)
        val second = files.openResult(metadata)
        assertFalse(files.stageRemoval(metadata.id, false) { true })
        first.close()
        assertFalse(files.stageRemoval(metadata.id, false) { true })
        assertEquals("locked", probeLease(files.directory(metadata).resolve(".downloads.lock")))
        assertEquals("complete", second.readAllBytes().decodeToString())
        second.close(); second.close()
        assertTrue(files.stageRemoval(metadata.id, false) { true })
        assertFalse(Files.exists(result))
        // Simulate a restart after atomic move but before unlink/Redis removal.
        assertTrue(files.stageRemoval(metadata.id, false) { true })
        files.removeStaged(metadata.id)
        assertFalse(Files.exists(files.root.resolve(".trash").resolve(metadata.id)))
    }

    @Test
    fun activeWorkerAndRejectedCleanupCannotRemoveJob() = withFiles { files, metadata ->
        requireNotNull(exclusiveLease(files.directory(metadata), ".worker.lock")).use {
            assertFalse(files.stageRemoval(metadata.id, false) { fail("must not authorize active cleanup") })
        }
        assertFalse(files.stageRemoval(metadata.id, false) { false })
        assertTrue(Files.isDirectory(files.directory(metadata)))
    }

    @Test
    fun storagePathsRejectTraversalAndForeignRoots() = withFiles { files, metadata ->
        assertFailsWith<IllegalArgumentException> { files.directory("../../outside") }
        assertFailsWith<IllegalArgumentException> { files.directory(metadata.copy(dir = files.root.parent.toString())) }
        assertFailsWith<IllegalArgumentException> { files.result(metadata.copy(resultFile = "../../outside.zip")) }
    }

    @Test
    fun statusUsesCommittedAvailabilityWithoutFilesystemProbe() = withFiles { files, metadata ->
        val repository = Mockito.mock(RedisJobRepository::class.java)
        Mockito.`when`(repository.find(metadata.id)).thenReturn(metadata)
        val service = JobService(BackendProperties(), repository, Mockito.mock(RedisJobQueue::class.java), files)
        assertFalse(Files.exists(files.result(metadata)))
        assertTrue(service.get(metadata.id).resultAvailable)
        assertFailsWith<NoSuchElementException> { service.openResult(metadata.id) }
        assertTrue(files.stageRemoval(metadata.id, false) { true }) // failed open released the lease
    }

    @Test
    fun admissionAndConfigLimitRejectBeforeCreatingStagedFiles() = withFiles { files, metadata ->
        val queue = Mockito.mock(RedisJobQueue::class.java)
        val properties = BackendProperties().apply { minFreeDiskBytes = 0; maxConfigBytes = 3 }
        val service = JobService(properties, Mockito.mock(RedisJobRepository::class.java), queue, files)
        val input = MockMultipartFile("input", "input.jar", "application/java-archive", byteArrayOf(1))
        assertFailsWith<ConfigTooLargeException> {
            service.submit(MockMultipartFile("config", ByteArray(4)), input, emptyList())
        }
        Mockito.verifyNoInteractions(queue)
        assertFailsWith<JobAdmissionException> {
            service.submit(MockMultipartFile("config", "{}".toByteArray()), input, emptyList())
        }
        assertFalse(Files.exists(files.root.resolve(".staging")))
        assertTrue(Files.exists(files.directory(metadata)))
    }

    @Test
    fun resultZipHasCompleteLogAndLevelZeroNestedJar() = withFiles { files, metadata ->
        val dir = files.directory(metadata)
        val jar = Files.write(dir.resolve("output.jar"), ByteArray(16384) { 65 })
        val log = Files.writeString(dir.resolve("log.txt"), "line\n".repeat(1000) + "final log tail")
        val zip = dir.resolve("result.zip")
        WorkerJobRunner.createResultZip(zip, jar, log)
        ZipFile(zip.toFile()).use { archive ->
            assertEquals(2, archive.size())
            val nested = archive.getEntry("output.jar")
            assertEquals(ZipEntry.DEFLATED, nested.method)
            assertTrue(nested.compressedSize >= nested.size)
            assertContentEquals(Files.readAllBytes(jar), archive.getInputStream(nested).use { it.readAllBytes() })
            assertEquals(Files.readString(log), archive.getInputStream(archive.getEntry("log.txt")).use { it.readAllBytes().decodeToString() })
        }
    }

    @Test
    fun failedZipPublicationKeepsPriorArtifactAndRemovesTemporaryFile() = withFiles { files, metadata ->
        val dir = files.directory(metadata)
        val zip = Files.writeString(dir.resolve("result.zip"), "previous complete artifact")
        val invalid = Files.createDirectory(dir.resolve("unreadable-directory"))
        assertFails { WorkerJobRunner.createResultZip(zip, invalid) }
        assertEquals("previous complete artifact", Files.readString(zip))
        Files.list(dir).use { paths -> assertFalse(paths.anyMatch { it.fileName.toString().startsWith(".result-") }) }
    }

    @Test
    fun recoveredAttemptLockWaitTimeoutIsRetryableNotTerminalFailure() = withFiles { files, metadata ->
        val marker = files.directory(metadata).resolve(".started")
        assertFailsWith<WorkerAttemptNotStartedException> { checkAttemptExit(-1, marker) }
        assertEquals(1, checkAttemptExit(1, marker)) // Actual bootstrap failure still terminates the job.
        Files.writeString(marker, "")
        assertEquals(-1, checkAttemptExit(-1, marker)) // Timeout while executing, not while waiting for the lock.
    }

    @Test
    fun timeoutAndHeartbeatFailureTerminateAndReapChild() {
        val process = startChild()
        assertEquals(-1, awaitProcess(process, 1, 1, 1) {})
        assertFalse(process.isAlive)
        val failed = startChild()
        assertFailsWith<IllegalStateException> { awaitProcess(failed, 0, 1, 1) { error("lease lost") } }
        assertFalse(failed.isAlive)
    }

    @Test
    fun interruptedWaitTerminatesChild() {
        val process = startChild()
        try {
            Thread.currentThread().interrupt()
            assertFailsWith<InterruptedException> { awaitProcess(process, 0, 1, 1) {} }
            assertFalse(process.isAlive)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted(); if (process.isAlive) terminateAndReap(process) }
    }

    @Test
    fun knownNativeDescendantTerminatesBeforeSlotRelease() {
        val process = startChild(tree = true)
        val pid = process.inputStream.bufferedReader().readLine().toLong()
        try {
            assertEquals(-1, awaitProcess(process, 1, 1, 1) {})
            assertFalse(process.isAlive)
            assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false))
        } finally { if (process.isAlive) terminateAndReap(process, graceSeconds = 1) }
    }

    private fun probeLease(path: Path): String {
        val java = Path.of(System.getProperty("java.home"), "bin",
            if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val classpath = Path.of(SleepingChild::class.java.protectionDomain.codeSource.location.toURI())
        val process = ProcessBuilder(java.toString(), "-Xmx16m", "-cp", classpath.toString(),
            SleepingChild::class.java.name, "probe", path.toString()).start()
        try {
            val result = process.inputStream.bufferedReader().readLine()
            assertTrue(process.waitFor(10, TimeUnit.SECONDS))
            assertEquals(0, process.exitValue())
            return result
        } finally { if (process.isAlive) terminateAndReap(process) }
    }

    private fun startChild(tree: Boolean = false): Process {
        val java = Path.of(System.getProperty("java.home"), "bin",
            if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val classpath = Path.of(SleepingChild::class.java.protectionDomain.codeSource.location.toURI())
        return ProcessBuilder(listOf(java.toString(), "-Xmx16m", "-cp", classpath.toString(),
            SleepingChild::class.java.name) + if (tree) listOf("tree") else emptyList()).start().also {
            if (!tree) assertEquals("ready", it.inputStream.bufferedReader().readLine())
        }
    }

    private fun withFiles(block: (JobFiles, JobMetadata) -> Unit) {
        val root = Files.createTempDirectory("grunt-backend-test-")
        try {
            val files = JobFiles(BackendProperties().apply { workDir = root })
            val id = UUID.randomUUID().toString()
            val dir = Files.createDirectories(files.directory(id))
            val metadata = JobMetadata(id, dir.toString(), JobStatus.SUCCESS, Instant.now(), Instant.now(), resultAvailable = true)
            block(files, metadata)
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }
}
