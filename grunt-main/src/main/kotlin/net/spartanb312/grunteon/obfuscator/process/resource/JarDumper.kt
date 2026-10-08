package net.spartanb312.grunteon.obfuscator.process.resource

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.produce
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.util.ClearClassNode
import net.spartanb312.grunteon.obfuscator.util.ImplLookupGetter
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.file.SingleEntryZipOutputStream
import net.spartanb312.grunteon.obfuscator.util.file.ZipCrcFinalSetter
import net.spartanb312.grunteon.obfuscator.util.file.corruptCRC32
import net.spartanb312.grunteon.obfuscator.util.file.corruptJarHeader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.invoke.MethodType
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.extension
import kotlin.io.path.name

object JarDumper {
    private class DumpEntry(
        val entry: ZipEntry,
        val compressed: ByteArray? = null,
        val openStream: (() -> InputStream)? = null,
        val release: () -> Unit = {}
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    context(instance: Grunteon)
    fun dumpJar(output: ResourceOutput) {
        val config = instance.globalConfig
        val outputPath = output.targetPath()?.toAbsolutePath()?.normalize()
        fun isOutput(path: java.nio.file.Path): Boolean {
            if (outputPath == null || path.fileSystem != outputPath.fileSystem) return false
            return path.toAbsolutePath().normalize() == outputPath ||
                    (java.nio.file.Files.exists(outputPath) && java.nio.file.Files.isSameFile(path, outputPath))
        }
        fun removed(name: String) = config.fileRemovePrefix.any { name.startsWith(it) } ||
                config.fileRemoveSuffix.any { name.endsWith(it) }
        fun entry(name: String) = ZipEntry(name).apply { if (config.removeTimeStamps) time = 0 }

        Logger.info("Dumping jar to ${output.description}")
        if (output.exists()) Logger.warn("Existing output file will be overridden!")
        output.openOutputStream().buffered(65536).use { directOut ->
            if (config.corruptHeaders) {
                Logger.info("Corrupting jar header...")
                corruptJarHeader(Xoshiro256PPRandom(getSeed(config.input, output.fileName, "corruptHeader")), directOut)
            }
            runBlocking {
                Logger.info("Building hierarchies...")
                val hierarchy = instance.workRes.classHierarchy()
                if (config.missingCheck) hierarchy.printMissing()
                val workerCount = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
                val budget = InFlightByteBudget(
                    System.getProperty("grunteon.dump.maxInFlightBytes")?.toLongOrNull()?.coerceAtLeast(1)
                        ?: (64L * 1024 * 1024)
                )

                suspend fun dumpClass(classNode: ClassNode): DumpEntry {
                    val missingList = if (config.missingCheck) hierarchy.checkMissing(classNode) else emptySet()
                    val classInfo = hierarchy.findClass(classNode.name)
                    check(classInfo != -1) { "Class ${classNode.name} not found in hierarchy!" }
                    val missingRef = missingList.isNotEmpty()
                    if (missingRef) {
                        Logger.error("Class ${classNode.name} missing reference:")
                        missingList.forEach { Logger.error(" - $it") }
                    }
                    val missingAny = config.missingCheck && (hierarchy.missingDependencies[classInfo] || missingRef)
                    val processable = instance.globalExclusion.test(classNode)
                    val useComputeMax = shouldUseComputeMax(config.forceComputeMax, missingAny, processable)
                    if (shouldWarnComputeMaxDueToMissing(config.forceComputeMax, missingAny, processable)) {
                        Logger.warn("Using COMPUTE_MAXS due to ${classNode.name} missing dependencies or reference.")
                    }
                    fun encode(computeMax: Boolean): ByteArray = ClassDumper(instance, hierarchy, computeMax).apply {
                        classNode.accept(ClearClassNode(Opcodes.ASM9, this))
                    }.toByteArray()
                    val bytes = try {
                        encode(useComputeMax)
                    } catch (error: Exception) {
                        Logger.error("Failed to dump class ${classNode.name}. Trying alternate frame mode")
                        error.printStackTrace()
                        try {
                            encode(!useComputeMax)
                        } catch (fallback: Exception) {
                            Logger.error("Failed to dump class ${classNode.name}!")
                            fallback.printStackTrace()
                            ByteArray(0)
                        }
                    }
                    // Bound compressed buffers separately from the P unavoidable ASM working arrays.
                    // An individual oversized class is admitted exclusively, rather than deadlocking the budget.
                    val reservation = budget.acquire(bytes.size.toLong() * 2 + 131072)
                    try {
                        val zipEntry = entry(classNode.name + ".class")
                        val buffer = ByteArrayOutputStream()
                        SingleEntryZipOutputStream(buffer).use { zip ->
                            zip.setLevel(config.compressionLevel)
                            zip.putNextEntry(zipEntry)
                            zip.write(bytes)
                            zip.closeEntry()
                        }
                        return DumpEntry(zipEntry, buffer.toByteArray(), release = reservation::close)
                    } catch (error: Throwable) {
                        reservation.close()
                        throw error
                    }
                }

                val completed = produce(Dispatchers.Default, capacity = Channel.RENDEZVOUS) {
                    val tasks = Channel<suspend () -> DumpEntry>(workerCount)
                    launch {
                        try {
                            for (path in instance.workRes.inputResourceSet.files()) {
                                if (path.extension == "class" || removed(path.name) || isOutput(path)) continue
                                tasks.send {
                                    DumpEntry(entry(instance.workRes.inputResourceSet.entryName(path)),
                                        openStream = { instance.workRes.inputResourceSet.openFile(path) })
                                }
                            }
                            for (classNode in instance.workRes.inputClassCollection) {
                                if (classNode.name == "module-info" || removed(classNode.name)) continue
                                tasks.send { dumpClass(classNode) }
                            }
                            for ((name, bytes) in instance.workRes.generatedResources) {
                                if (!removed(name)) tasks.send {
                                    DumpEntry(entry(name), openStream = { bytes.inputStream() })
                                }
                            }
                        } finally {
                            tasks.close()
                        }
                    }
                    repeat(workerCount) {
                        launch {
                            for (task in tasks) {
                                val result = task()
                                try {
                                    send(result)
                                } catch (error: Throwable) {
                                    result.release()
                                    throw error
                                }
                            }
                        }
                    }
                }
                withContext(Dispatchers.IO) {
                    ZipOutputStream(directOut).use { zip ->
                        zip.setLevel(config.compressionLevel)
                        if (config.archiveComment.isNotEmpty()) zip.setComment(config.archiveComment)
                        if (config.corruptCRC32) {
                            Logger.info("Corrupting CRC32...")
                            zip.corruptCRC32(Xoshiro256PPRandom(getSeed(config.input, output.fileName, "corruptCRC32")))
                        }
                        for (path in instance.workRes.inputResourceSet.directories()) {
                            zip.putNextEntry(entry(instance.workRes.inputResourceSet.entryName(path) + "/"))
                            zip.closeEntry()
                        }
                        // Legacy file entries were compressed by an uncorrupted per-entry stream.
                        // Keep corruptCRC32 limited to directory entries and preserve its RNG consumption.
                        if (config.corruptCRC32) ZipCrcFinalSetter.setCrc(zip, java.util.zip.CRC32())
                        @Suppress("UNCHECKED_CAST")
                        val entries = varEntries.get(zip) as Vector<Any>
                        Logger.info("Writing files...")
                        for (result in completed) {
                            try {
                                val bytes = result.compressed
                                if (bytes != null) {
                                    val offset = varWritten.get(zip) as Long
                                    entries.add(xEntryConstructor.invoke(result.entry, offset))
                                    directOut.write(bytes)
                                    varWritten.set(zip, offset + bytes.size.toLong())
                                } else {
                                    // The legacy raw-entry path permits duplicate resource names (last entry wins).
                                    @Suppress("UNCHECKED_CAST")
                                    val names = varNames.get(zip) as HashSet<String>
                                    names.remove(result.entry.name)
                                    zip.putNextEntry(result.entry)
                                    result.openStream!!().use { it.copyTo(zip, 65536) }
                                    zip.closeEntry()
                                }
                            } finally {
                                result.release()
                            }
                        }
                    }
                }
            }
        }
        output.sizeBytes()?.let { Logger.info("Dumped jar size: $it bytes") }
    }

    private val lookup = ImplLookupGetter.getLookup()
    private val varEntries = lookup.findVarHandle(ZipOutputStream::class.java, "xentries", Vector::class.java)
    private val varWritten = lookup.findVarHandle(ZipOutputStream::class.java, "written", Long::class.java)
    private val varNames = lookup.findVarHandle(ZipOutputStream::class.java, "names", HashSet::class.java)
    private val xEntryConstructor = lookup.findConstructor(
        Class.forName("java.util.zip.ZipOutputStream\$XEntry"),
        MethodType.methodType(Void.TYPE, ZipEntry::class.java, Long::class.java)
    )

    internal fun shouldUseComputeMax(
        forceComputeMax: Boolean,
        missingAny: Boolean,
        processableByGlobalExclusion: Boolean
    ): Boolean {
        val globallyExcluded = !processableByGlobalExclusion
        return forceComputeMax || missingAny || globallyExcluded
    }

    internal fun shouldWarnComputeMaxDueToMissing(
        forceComputeMax: Boolean,
        missingAny: Boolean,
        processableByGlobalExclusion: Boolean
    ): Boolean {
        val globallyExcluded = !processableByGlobalExclusion
        return missingAny && !forceComputeMax && !globallyExcluded
    }

}