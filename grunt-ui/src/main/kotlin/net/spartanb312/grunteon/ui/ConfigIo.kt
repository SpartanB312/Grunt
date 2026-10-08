package net.spartanb312.grunteon.ui

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

/** One FIFO for reads and writes. Cancelling a UI await never cancels an accepted write. */
internal class ConfigIoQueue(dispatcher: CoroutineDispatcher = Dispatchers.IO) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val requests = Channel<() -> Unit>(Channel.UNLIMITED)
    private val worker = scope.launch {
        for (request in requests) request()
    }

    fun <T> submit(operation: () -> T): Deferred<T> {
        val result = CompletableDeferred<T>()
        val accepted = requests.trySend {
            try {
                result.complete(operation())
            } catch (error: Throwable) {
                result.completeExceptionally(error)
            }
        }
        if (accepted.isFailure) result.completeExceptionally(IllegalStateException("Config IO is closed"))
        return result
    }

    suspend fun flush() { submit {}.await() }

    fun stop() {
        requests.close()
        worker.invokeOnCompletion { scope.cancel() }
    }

    suspend fun close() {
        stop()
        worker.join()
    }
}

/** Do not fall back to a non-atomic replacement: on an unsupported filesystem keep the original intact. */
internal fun atomicConfigWrite(path: Path, write: (Path) -> Unit) {
    val requested = path.toAbsolutePath().normalize()
    val target = if (Files.isSymbolicLink(requested)) requested.toRealPath() else requested
    Files.createDirectories(target.parent)
    val temporary = Files.createTempFile(target.parent, ".grunteon-", ".json.tmp")
    try {
        write(temporary)
        FileChannel.open(temporary, WRITE).use { it.force(true) }
        Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temporary)
    }
}