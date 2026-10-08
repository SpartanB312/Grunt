package net.spartanb312.grunteon.backend

import org.springframework.stereotype.Component
import java.io.Closeable
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class FileLease(private val channel: FileChannel, private val lock: FileLock,
                         private val released: () -> Unit) : Closeable {
    private var closed = false
    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        try { try { lock.release() } finally { channel.close() } } finally { released() }
    }
}

// On POSIX, closing ANY descriptor for an inode can drop this JVM's other fcntl locks on it.
// Reject overlapping acquisitions before opening a second channel, not merely after tryLock fails.
private val localLeaseLock = ReentrantLock()
private val localLeaseReleased = localLeaseLock.newCondition()
private val localLeasePaths = HashSet<Path>()

private fun fileLease(path: Path, shared: Boolean, wait: Boolean): FileLease? {
    val key = path.toAbsolutePath().normalize()
    localLeaseLock.withLock {
        while (!localLeasePaths.add(key)) {
            if (!wait) return null
            localLeaseReleased.await()
        }
    }
    val release = {
        localLeaseLock.withLock { localLeasePaths.remove(key); localLeaseReleased.signalAll() }
    }
    var channel: FileChannel? = null
    try {
        val opened = FileChannel.open(key, CREATE, READ, WRITE)
        channel = opened
        val lock = if (wait) opened.lock(0, Long.MAX_VALUE, shared)
            else try { opened.tryLock(0, Long.MAX_VALUE, shared) } catch (_: OverlappingFileLockException) { null }
        if (lock != null) return FileLease(opened, lock, release)
        opened.close()
        release()
        return null
    } catch (error: Throwable) {
        try { channel?.close() } finally { release() }
        throw error
    }
}

internal fun exclusiveLease(dir: Path, name: String, wait: Boolean = false): FileLease? =
    fileLease(dir.resolve(name), shared = false, wait = wait)

@Component
class JobFiles(properties: BackendProperties) {
    val root: Path = properties.workDir.toAbsolutePath().normalize().also { Files.createDirectories(it) }.toRealPath()
    private data class Readers(val lease: FileLease, var count: Int)
    private val readers = HashMap<Path, Readers>()

    fun directory(id: String): Path {
        require(UUID.fromString(id).toString() == id) { "Invalid job id" }
        return root.resolve(id)
    }

    fun staging(id: String): Path = root.resolve(".staging").resolve(directory(id).fileName)
    private fun trash(id: String): Path = root.resolve(".trash").resolve(directory(id).fileName)

    fun directory(metadata: JobMetadata): Path = directory(metadata.id).also {
        val stored = Path.of(metadata.dir).toAbsolutePath().normalize()
        require(stored == it || (stored.fileName == it.fileName && stored.parent?.toRealPath() == root)) {
            "Job storage belongs to another work directory"
        }
        require(!Files.isSymbolicLink(it)) { "Job directory must not be a symbolic link" }
    }

    fun result(metadata: JobMetadata): Path {
        val dir = directory(metadata)
        val result = dir.resolve(metadata.resultFile).normalize()
        require(result.startsWith(dir) && result != dir) { "Invalid result path" }
        return result
    }

    // JVM file locks cannot overlap, even when both are shared. Reference-count one shared OS lease per job.
    @Synchronized
    private fun downloadLease(dir: Path): Closeable {
        val existing = readers[dir]
        if (existing == null) {
            val lease = fileLease(dir.resolve(".downloads.lock"), shared = true, wait = false)
                ?: throw IllegalStateException("Job cleanup is in progress")
            readers[dir] = Readers(lease, 1)
        } else existing.count++
        var closed = false
        return Closeable {
            synchronized(this) {
                if (!closed) {
                    closed = true
                    val current = readers.getValue(dir)
                    if (--current.count == 0) {
                        readers.remove(dir)
                        current.lease.close()
                    }
                }
            }
        }
    }

    fun openResult(metadata: JobMetadata): InputStream {
        require(metadata.status == JobStatus.SUCCESS && !metadata.deleting && metadata.resultAvailable) {
            "Result for job ${metadata.id} is not available"
        }
        val lease = downloadLease(directory(metadata))
        try {
            val path = result(metadata)
            if (!Files.isRegularFile(path, NOFOLLOW_LINKS)) throw NoSuchElementException("Result is not available")
            return object : FilterInputStream(Files.newInputStream(path)) {
                override fun close() { try { super.close() } finally { lease.close() } }
            }
        } catch (error: Throwable) {
            lease.close()
            throw error
        }
    }

    /** Moves under exclusive worker/download leases; actual unlink happens only after the leases close (Windows). */
    fun stageRemoval(id: String, upload: Boolean, authorize: () -> Boolean): Boolean {
        val source = if (upload && Files.exists(staging(id), NOFOLLOW_LINKS)) staging(id) else directory(id)
        val target = trash(id)
        if (!Files.exists(source, NOFOLLOW_LINKS)) return authorize()
        require(!Files.isSymbolicLink(source)) { "Refusing to clean a symbolic job directory" }
        exclusiveLease(source, ".worker.lock")?.use {
            exclusiveLease(source, ".downloads.lock")?.use {
                if (!authorize()) return false
                Files.createDirectories(target.parent)
                check(!Files.exists(target, NOFOLLOW_LINKS)) { "Job cleanup target already exists" }
                Files.move(source, target, ATOMIC_MOVE)
                return true
            }
        }
        return false
    }

    fun removeStaged(id: String) {
        val target = trash(id)
        // Only this validated UUID below this work root is removable. walkFileTree never follows symlinks.
        if (!Files.exists(target, NOFOLLOW_LINKS)) return
        Files.walkFileTree(target, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, error: java.io.IOException?): FileVisitResult {
                if (error != null) throw error
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
