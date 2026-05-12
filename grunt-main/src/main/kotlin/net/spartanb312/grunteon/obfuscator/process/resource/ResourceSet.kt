package net.spartanb312.grunteon.obfuscator.process.resource

import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.io.path.walk
import kotlin.jvm.optionals.getOrNull

sealed class ResourceSet {

    abstract operator fun get(path: Path): List<ResourceEntry>

    abstract operator fun get(path: String): List<ResourceEntry>

    class ResourceEntry(val path: Path, var content: ByteArray)

    class Single(val root: Path) : ResourceSet() {
        val cache = ConcurrentHashMap<String, Optional<ResourceEntry>>()

        fun files(): Sequence<Path> {
            return root.walk().filter { !it.isDirectory() }
        }

        fun directories(): Sequence<Path> {
            return root.walk().filter { it.isDirectory() }
        }

        fun entryName(path: Path): String {
            return path.pathString.removePrefix("/")
        }

        fun readFile(path: Path): ByteArray {
            return get(path).firstOrNull()?.content ?: path.readBytes()
        }

        override fun get(path: Path): List<ResourceEntry> {
            require(path.fileSystem == root.fileSystem) { "Path must be on the same file system as the root" }
            val absolutePath = path.absolute()
            return cache.computeIfAbsent(absolutePath.pathString) {
                if (path.exists()) {
                    Optional.of(ResourceEntry(absolutePath, path.readBytes()))
                } else {
                    Optional.empty()
                }
            }.getOrNull()?.let { listOf(it) } ?: emptyList()
        }

        override operator fun get(path: String): List<ResourceEntry> {
            return get(root.resolve(path))
        }
    }

    class Composite(val sets: List<ResourceSet>) : ResourceSet() {
        override fun get(path: Path): List<ResourceEntry> {
            return sets.flatMap { it[path].asSequence() }
        }

        override operator fun get(path: String): List<ResourceEntry> {
            return sets.flatMap { it[path].asSequence() }
        }
    }

}
