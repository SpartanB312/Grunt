package net.spartanb312.grunteon.obfuscator.process.resource

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.produce
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.hierarchy.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.util.ClearClassNode
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.isExcluded
import net.spartanb312.grunteon.obfuscator.util.file.corruptCRC32
import net.spartanb312.grunteon.obfuscator.util.file.corruptJarHeader
import org.objectweb.asm.Opcodes
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

object JarDumper {
    private data class PendingZipEntry(
        val entryName: String,
        val content: ByteArray
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    context(instance: Grunteon)
    fun dumpJar(outputFile: Path) {
        dumpJar(PathResourceOutput(outputFile))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    context(instance: Grunteon)
    fun dumpJar(output: ResourceOutput) {
        val config = instance.obfConfig

        fun checkFileNameRemove(name: String): Boolean {
            return config.fileRemovePrefix.any { name.startsWith(it) }
                    || config.fileRemoveSuffix.any { name.endsWith(it) }
        }

        Logger.info("Dumping jar to ${output.description}")
        if (output.exists()) Logger.warn("Existing output file will be overridden!")
        val directOut = output.openOutputStream().buffered(65536)
        // Corrupt header
        if (config.corruptHeaders) {
            Logger.info("Corrupting jar header...")
            val random = Xoshiro256PPRandom(
                getSeed(
                    config.input,
                    output.fileName,
                    "corruptHeader",
                )
            )
            corruptJarHeader(random, directOut)
        }
        runBlocking {
            val classes = produce(
                Dispatchers.Default,
                capacity = Channel.BUFFERED
            ) {
                // Build hierarchy
                Logger.info("Building hierarchies...")
                val hierarchy =
                    ClassHierarchy.build(instance.workRes.allClassCollection, instance.workRes::getClassNode)
                // Writing class
                if (config.missingCheck) hierarchy.printMissing()
                launch {
                    instance.workRes.inputResourceSet.files()
                        .filter { it.extension != "class" }
                        .filterNot { checkFileNameRemove(it.name) }
                        .forEach {
                            launch {
                                send(
                                    PendingZipEntry(
                                        instance.workRes.inputResourceSet.entryName(it),
                                        instance.workRes.inputResourceSet.readFile(it)
                                    )
                                )
                            }
                        }
                }
                for (classNode in instance.workRes.inputClassCollection) {
                    // File remove
                    if (classNode.name == "module-info" || checkFileNameRemove(classNode.name)) continue
                    val missingList = hierarchy.checkMissing(classNode)
                    val classInfo = hierarchy.findClass(classNode.name)
                    check(classInfo != -1) { "Class ${classNode.name} not found in hierarchy!" }
                    launch(Dispatchers.Default) {
                        // Dependency check
                        val missingRef = missingList.isNotEmpty()
                        if (missingRef && config.missingCheck) {
                            Logger.error("Class ${classNode.name} missing reference:")
                            for (missing in missingList) {
                                Logger.error(" - $missing")
                            }
                        }
                        val missingAny = (hierarchy.missingDependencies[classInfo] || missingRef) && config.missingCheck
                        val useComputeMax = config.forceComputeMax || missingAny || classNode.isExcluded
                        val missing = missingAny && !config.forceComputeMax && !classNode.isExcluded
                        // Write zip entry
                        val entryName = classNode.name + ".class"
                        val byteArray = try {
                            if (missing) Logger.warn("Using COMPUTE_MAXS due to ${classNode.name} missing dependencies or reference.")
                            ClassDumper(instance, hierarchy, useComputeMax).apply {
                                classNode.accept(ClearClassNode(Opcodes.ASM9, this))
                            }.toByteArray()
                        } catch (exception: Exception) {
                            Logger.error("Failed to dump class ${classNode.name}. Trying ${if (useComputeMax) "COMPUTE_FRAMES" else "COMPUTE_MAXS"}")
                            exception.printStackTrace()
                            try {
                                ClassDumper(instance, hierarchy, !useComputeMax).apply {
                                    classNode.accept(ClearClassNode(Opcodes.ASM9, this))
                                }.toByteArray()
                            } catch (exception: Exception) {
                                Logger.error("Failed to dump class ${classNode.name}!")
                                exception.printStackTrace()
                                ByteArray(0)
                            }
                        }

                        send(PendingZipEntry(entryName, byteArray))
                    }
                }
                instance.workRes.generatedResources.forEach { (name, content) ->
                    if (!checkFileNameRemove(name)) send(PendingZipEntry(name, content))
                }
            }

            withContext(Dispatchers.IO) {
                ZipOutputStream(directOut).use { zipOut ->
                    zipOut.setLevel(config.compressionLevel)
                    // Archive comment
                    if (config.archiveComment.isNotEmpty()) zipOut.setComment(config.archiveComment)
                    // Corrupt CRC32
                    if (config.corruptCRC32) {
                        Logger.info("Corrupting CRC32...")
                        val random = Xoshiro256PPRandom(
                            getSeed(
                                config.input,
                                output.fileName,
                                "corruptCRC32",
                            )
                        )
                        zipOut.corruptCRC32(random)
                    }

                    instance.workRes.inputResourceSet.directories()
                        .forEach {
                            val zipEntry = ZipEntry(instance.workRes.inputResourceSet.entryName(it) + "/")
                            if (config.removeTimeStamps) zipEntry.time = 0
                            zipOut.putNextEntry(zipEntry)
                            zipOut.closeEntry()
                        }

                    Logger.info("Writing files...")
                    for ((entryName, content) in classes) {
                        val zipEntry = ZipEntry(entryName)
                        if (config.removeTimeStamps) zipEntry.time = 0
                        zipOut.putNextEntry(zipEntry)
                        zipOut.write(content)
                        zipOut.closeEntry()
                    }
                    // TODO: dump mappings
                }
            }
        }
    }
}
