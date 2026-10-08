package net.spartanb312.grunteon.obfuscator.process.resource

import kotlinx.coroutines.*
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import net.spartanb312.grunteon.obfuscator.util.PHANTOM_CLASS
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.hasAnnotation
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.*

class ResourcePerformanceRegressionTest {
    @Test
    fun independentlyOwnedZipFileSystemsCloseWithoutClosingExternalBorrower() = workspace { dir ->
        val jar = dir.resolve("input.jar")
        writeZip(jar, mapOf("assets/value.txt" to "value".toByteArray()))
        FileSystems.newFileSystem(URI.create("jar:" + jar.toUri()), emptyMap<String, String>()).use { external ->
            val first = WorkResources.read(jar)
            val second = WorkResources.read(jar)
            val firstFs = first.inputResourceSet.root.fileSystem
            val secondFs = second.inputResourceSet.root.fileSystem
            try {
                assertNotSame(firstFs, secondFs)
                assertNotSame(external, firstFs)
                first.close()
                first.close()
                assertFalse(firstFs.isOpen)
                assertTrue(secondFs.isOpen)
                assertTrue(external.isOpen)
                assertEquals("value", second.inputResourceSet.readFile(second.inputResourceSet.root.resolve("assets/value.txt")).decodeToString())
                WorkResources.read(external.getPath("/")).use { borrowed ->
                    assertSame(external, borrowed.inputResourceSet.root.fileSystem)
                }
                assertTrue(external.isOpen, "A borrowed ZIP filesystem belongs to its caller")
                assertEquals("value", Files.readString(external.getPath("/assets/value.txt")))
            } finally {
                first.close()
                second.close()
            }
            assertFalse(secondFs.isOpen)
        }
    }

    @Test
    fun untouchedResourcesStreamWithoutCachingAndMutableOverlayWins() = workspace { dir ->
        val file = dir.resolve("asset.bin")
        val original = ByteArray(128 * 1024) { it.toByte() }
        Files.write(file, original)
        val resources = ResourceSet.Single(dir)
        repeat(3) { assertContentEquals(original, resources.readFile(file)) }
        assertTrue(resources.cache.isEmpty(), "Streaming must not retain untouched assets")
        val edited = resources["asset.bin"].single()
        edited.content = "edited-overlay".toByteArray()
        Files.writeString(file, "changed-on-disk")
        assertEquals("edited-overlay", resources.openFile(file).use { it.readBytes().decodeToString() })
        assertSame(edited, resources[file].single())
        assertEquals(1, resources.cache.size)
        assertEquals("asset.bin", resources.entryName(file))
    }

    @Test
    fun libraryHeadersHydrateOnceAndPublicMapTracksTheFullNode() = workspace { dir ->
        val input = Files.createDirectory(dir.resolve("input"))
        val library = dir.resolve("library.jar")
        writeZip(library, mapOf("library/Sample.class" to sampleClass("library/Sample")))
        WorkResources.read(input, listOf(library)).use { resources ->
            val header = assertNotNull(resources.getClassMetadata("library/Sample"))
            assertSame(header, resources.libraryClassMap[header.name])
            assertTrue(header.hasAnnotation(PHANTOM_CLASS))
            assertEquals(23, header.fields.single().value)
            assertEquals("Lexample/Marker;", header.visibleAnnotations.single().desc)
            assertTrue(header.methods.all { it.instructions.size() == 0 })
            val full = assertNotNull(resources.getClassNode(header.name))
            assertNotSame(header, full)
            assertFalse(full.hasAnnotation(PHANTOM_CLASS))
            assertTrue(full.methods.single().instructions.size() > 0)
            assertSame(full, resources.libraryClassMap[header.name])
            assertSame(full, resources.getClassMetadata(header.name))
            assertSame(full, resources.getClassNode(header.name))
            assertTrue(resources.libraryResourceSets.values.all { it.cache.isEmpty() })
        }
    }

    @Test
    fun concurrentHydrationNeverReturnsAHeaderCapturedBeforeAnotherWorkersHydration() = workspace { dir ->
        val input = Files.createDirectory(dir.resolve("input"))
        val library = dir.resolve("library.jar")
        writeZip(library, mapOf("library/Race.class" to sampleClass("library/Race")))
        WorkResources.read(input, listOf(library)).use { resources ->
            val name = "library/Race"
            val header = assertNotNull(resources.getClassMetadata(name))
            val observedHeader = CountDownLatch(1)
            val returnOldHeader = CountDownLatch(1)
            val delayedThread = AtomicReference<Thread>()
            val pauseOnce = AtomicBoolean(true)
            // Hook only the public-map read, not production hydration itself. The first worker
            // captures a header; the second fully hydrates/removes its source before it returns.
            val map = object : ConcurrentHashMap<String, ClassNode>(resources.libraryClassMap) {
                override fun get(key: String): ClassNode? {
                    val captured = super.get(key)
                    if (key == name && Thread.currentThread() === delayedThread.get() &&
                        pauseOnce.compareAndSet(true, false)
                    ) {
                        observedHeader.countDown()
                        check(returnOldHeader.await(5, TimeUnit.SECONDS)) { "Timed out waiting for competing hydration" }
                    }
                    return captured
                }
            }
            WorkResources::class.java.getDeclaredField("libraryClassMap").apply { isAccessible = true }
                .set(resources, map)
            val workers = Executors.newFixedThreadPool(2)
            try {
                val delayed = workers.submit<ClassNode?> {
                    delayedThread.set(Thread.currentThread())
                    resources.getClassNode(name)
                }
                assertTrue(observedHeader.await(5, TimeUnit.SECONDS))
                val competing = workers.submit<ClassNode?> { resources.getClassNode(name) }
                val full = assertNotNull(competing.get(5, TimeUnit.SECONDS))
                assertNotSame(header, full)
                assertTrue(full.methods.single().instructions.size() > 0)
                assertSame(full, map[name])
                returnOldHeader.countDown()
                assertSame(full, delayed.get(5, TimeUnit.SECONDS), "A captured stale header is not a full-body result")
            } finally {
                returnOldHeader.countDown()
                workers.shutdownNow()
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun hydrationNeverOverwritesAPluginReplacementAndMissesAllowGeneratedClasses() = workspace { dir ->
        val input = Files.createDirectory(dir.resolve("input"))
        val library = dir.resolve("library.jar")
        writeZip(library, mapOf("library/Sample.class" to sampleClass("library/Sample")))
        WorkResources.read(input, listOf(library)).use { resources ->
            val replacement = node("library/Sample")
            resources.libraryClassMap[replacement.name] = replacement
            assertSame(replacement, resources.getClassNode(replacement.name))
            val missing = "unavailable/GeneratedRegressionClass"
            repeat(4) { assertNull(resources.getClassMetadata(missing)) }
            assertEquals(setOf(missing), missingClasses(resources))
            val generated = node(missing)
            resources.addGeneratedClass(generated)
            assertFalse(missing in missingClasses(resources))
            assertSame(generated, resources.getClassNode(missing))
            val directName = "unavailable/DirectMapRegressionClass"
            assertNull(resources.getClassNode(directName))
            val direct = node(directName)
            resources.inputClassMap[directName] = direct
            assertSame(direct, resources.getClassMetadata(directName), "Public map additions bypass negative cache")
            resources.invalidateMissingClasses()
            assertTrue(missingClasses(resources).isEmpty())
        }
    }

    @Test
    fun fullBodyLookupAfterCloseFailsExplicitlyButSnapshotDataRemainsInspectable() = workspace { dir ->
        val input = Files.createDirectory(dir.resolve("input"))
        Files.write(input.resolve("Input.class"), sampleClass("Input"))
        val library = dir.resolve("library.jar")
        writeZip(library, mapOf("Cold.class" to sampleClass("Cold"), "Warm.class" to sampleClass("Warm")))
        val resources = WorkResources.read(input, listOf(library))
        val cold = assertNotNull(resources.getClassMetadata("Cold"))
        val warm = assertNotNull(resources.getClassNode("Warm"))
        val inputs = resources.inputClassCollection.toList()
        resources.close()
        assertFailsWith<IllegalStateException> { resources.getClassNode("Cold") }
        assertFailsWith<IllegalStateException> { resources.getClassNode("Warm") }
        assertFailsWith<IllegalStateException> { resources.getClassNode("Input") }
        assertSame(cold, resources.getClassMetadata("Cold"))
        assertSame(warm, resources.getClassMetadata("Warm"))
        assertSame(inputs.single(), resources.inputClassCollection.single())
        assertTrue(inputs.single().methods.single().instructions.size() > 0)
    }

    @Test
    fun hierarchySnapshotReusesFirstBuildAfterLazyAncestorLookup() = workspace { dir ->
        WorkResources.read(dir).use { resources ->
            resources.addGeneratedClass(node("snapshot/Input"))
            val first = resources.classHierarchy()
            assertTrue(resources.libraryClassMap.containsKey("java/lang/Object"))
            assertSame(first, resources.classHierarchy(), "Ancestors loaded during build belong to the cached snapshot")
            assertSame(first, resources.classHierarchy())
        }
    }

    @Test
    fun hierarchySnapshotInvalidatesNamesEdgesMembershipAndIdentity() = workspace { dir ->
        WorkResources.read(dir).use { resources ->
            val a = node("snapshot/A")
            val b = node("snapshot/B")
            val input = node("snapshot/Input")
            listOf(a, b, input).forEach(resources::addGeneratedClass)
            var previous = resources.classHierarchy(includeLibraries = false)
            assertSame(previous, resources.classHierarchy(includeLibraries = false))
            fun changed() {
                val next = resources.classHierarchy(includeLibraries = false)
                assertNotSame(previous, next)
                assertSame(next, resources.classHierarchy(includeLibraries = false))
                previous = next
            }
            input.superName = a.name
            changed()
            assertTrue(previous.isSubType(input.name, a.name))
            input.interfaces.add(b.name)
            changed()
            assertTrue(previous.isSubType(input.name, b.name))
            resources.inputClassMap.remove(input.name)
            input.name = "snapshot/Renamed"
            resources.inputClassMap[input.name] = input
            changed()
            assertEquals(-1, previous.findClass("snapshot/Input"))
            val replacement = node(input.name).apply { superName = a.name; interfaces.add(b.name) }
            resources.inputClassMap[input.name] = replacement
            changed()
            assertSame(replacement, previous.classNodes[previous.findClass(input.name)])
            resources.addGeneratedClass(node("snapshot/Added"))
            changed()
            resources.inputClassMap.remove("snapshot/Added")
            changed()
            val objectHeader = assertNotNull(resources.getClassMetadata("java/lang/Object"))
            resources.libraryClassMap[objectHeader.name] = node(objectHeader.name).apply { superName = null }
            changed()
        }
    }

    @Test
    fun byteBudgetCancelsWaitersAndReleasesOversizedReservationsExactlyOnce() = runBlocking {
        withTimeout(5_000) {
            val budget = InFlightByteBudget(10)
            val first = budget.acquire(6)
            val cancelled = async(start = CoroutineStart.UNDISPATCHED) { budget.acquire(8) }
            assertFalse(cancelled.isCompleted)
            cancelled.cancelAndJoin()
            val rest = budget.acquire(4)
            val oversized = async(start = CoroutineStart.UNDISPATCHED) { budget.acquire(Long.MAX_VALUE) }
            assertFalse(oversized.isCompleted)
            first.close()
            yield()
            assertFalse(oversized.isCompleted, "Oversized entry must wait until all reservations are released")
            rest.close()
            val exclusive = oversized.await()
            val small = async(start = CoroutineStart.UNDISPATCHED) { budget.acquire(1) }
            assertFalse(small.isCompleted)
            exclusive.close()
            exclusive.close()
            small.await().close()
            val all = budget.acquire(10)
            val blocked = async(start = CoroutineStart.UNDISPATCHED) { budget.acquire(1) }
            assertFalse(blocked.isCompleted, "Double close must not inflate capacity")
            all.close()
            blocked.await().close()
        }
    }

    @Test
    fun byteBudgetSupportsReentrantResumeAndBoundsConcurrentWeight() = runBlocking {
        withTimeout(5_000) {
            val budget = InFlightByteBudget(16)
            val held = budget.acquire(16)
            val done = CompletableDeferred<Unit>()
            val reentrant = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                budget.acquire(8).use { budget.acquire(8).use { done.complete(Unit) } }
            }
            assertFalse(done.isCompleted)
            held.close() // Resumes Unconfined waiter inline; it immediately acquires and releases again.
            done.await()
            reentrant.join()
            val active = AtomicInteger()
            coroutineScope {
                repeat(80) { index ->
                    launch {
                        val weight = index % 7 + 1
                        budget.acquire(weight.toLong()).use {
                            assertTrue(active.addAndGet(weight) <= 16)
                            try { repeat(3) { yield() } } finally { active.addAndGet(-weight) }
                        }
                    }
                }
            }
            assertEquals(0, active.get())
            budget.acquire(16).close()
        }
    }

    @Test
    fun mixedStreamingZipHasReadableLocalOffsetsCrcAndMutableResources() = workspace { dir ->
        val input = dir.resolve("input.jar")
        val output = dir.resolve("output.jar")
        val original = ByteArray(96 * 1024) { (it * 37).toByte() }
        writeZip(input, linkedMapOf(
            "assets/" to byteArrayOf(),
            "assets/plain.bin" to original,
            "assets/тест.txt" to "original".toByteArray(),
            "input/Sample.class" to sampleClass("input/Sample")
        ))
        WorkResources.read(input).use { resources ->
            resources.getInputResource("assets/тест.txt")!!.content = "overlay".toByteArray()
            repeat(12) { index ->
                resources.addGeneratedClass(ClassNode().apply { ClassReader(sampleClass("generated/C" + index)).accept(this, 0) })
            }
            resources.addGeneratedResource("generated/data.bin", byteArrayOf(9, 8, 7))
            val config = GlobalConfig(input = input.toString(), output = output.toString(),
                removeTimeStamps = true, archiveComment = "mixed-streams", dumpMappings = false)
            val instance = Grunteon(config, ObfuscationIO(PathResourceInput(input)), resources, emptyList())
            context(instance) { JarDumper.dumpJar(PathResourceOutput(output)) }
            ZipFile(output.toFile()).use { zip ->
                assertEquals("mixed-streams", zip.comment)
                assertNotNull(zip.getEntry("assets/"))
                assertContentEquals(original, zip.getInputStream(zip.getEntry("assets/plain.bin")).use { it.readBytes() })
                assertEquals("overlay", zip.getInputStream(zip.getEntry("assets/тест.txt")).use { it.readBytes().decodeToString() })
                assertContentEquals(byteArrayOf(9, 8, 7), zip.getInputStream(zip.getEntry("generated/data.bin")).use { it.readBytes() })
                for (entry in zip.entries().asSequence()) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    assertEquals(entry.crc, CRC32().apply { update(bytes) }.value, entry.name)
                    assertEquals(0L, entry.time, entry.name)
                    if (entry.name.endsWith(".class")) assertEquals(entry.name.removeSuffix(".class"), ClassReader(bytes).className)
                }
            }
            val sequentialNames = mutableSetOf<String>()
            ZipInputStream(Files.newInputStream(output)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    sequentialNames.add(entry.name)
                    zip.readBytes() // ZipInputStream verifies DEFLATE descriptors and CRC on each entry.
                    zip.closeEntry()
                }
            }
            val expectedNames = setOf("assets/", "input/", "assets/plain.bin", "assets/тест.txt", "input/Sample.class", "generated/data.bin") +
                    (0 until 12).map { "generated/C" + it + ".class" }
            assertEquals(expectedNames, sequentialNames)
            checkCentralDirectoryOffsets(Files.readAllBytes(output), sequentialNames)
            assertEquals(1, resources.inputResourceSet.cache.size, "Untouched resource bytes must remain uncached")
        }
    }

    @Test
    fun disablingMissingCheckSkipsReferenceLookupButKeepsFrameHierarchy() = workspace { dir ->
        for (enabled in listOf(false, true)) {
            val input = Files.createDirectory(dir.resolve("input-" + enabled))
            WorkResources.read(input).use { resources ->
                val owner = node("missingcheck/Caller").apply {
                    methods.add(MethodNode(ACC_PUBLIC or ACC_STATIC, "run", "()V", null, null).apply {
                        repeat(20) { instructions.add(MethodInsnNode(INVOKESTATIC, "missingcheck/Unavailable", "call", "()V", false)) }
                        instructions.add(InsnNode(RETURN))
                    })
                }
                resources.addGeneratedClass(owner)
                val output = dir.resolve("output-" + enabled + ".jar")
                val config = GlobalConfig(input = input.toString(), output = output.toString(), missingCheck = enabled, dumpMappings = false)
                val instance = Grunteon(config, ObfuscationIO(PathResourceInput(input)), resources, emptyList())
                context(instance) { JarDumper.dumpJar(PathResourceOutput(output)) }
                assertEquals(enabled, "missingcheck/Unavailable" in missingClasses(resources))
                assertTrue(resources.libraryClassMap.containsKey("java/lang/Object"))
                ZipFile(output.toFile()).use { zip ->
                    assertEquals(owner.name, ClassReader(zip.getInputStream(zip.getEntry(owner.name + ".class")).use { it.readBytes() }).className)
                }
            }
        }
    }

    @Test
    fun duplicateInputAndGeneratedResourcesRemainReadableWithLastEntryLookup() = workspace { dir ->
        val input = Files.createDirectory(dir.resolve("input"))
        Files.writeString(input.resolve("shared.txt"), "input-value")
        WorkResources.read(input).use { resources ->
            resources.addGeneratedResource("shared.txt", "generated-value".toByteArray())
            val output = dir.resolve("duplicates.jar")
            val config = GlobalConfig(input = input.toString(), output = output.toString(), dumpMappings = false)
            val instance = Grunteon(config, ObfuscationIO(PathResourceInput(input)), resources, emptyList())
            context(instance) { JarDumper.dumpJar(PathResourceOutput(output)) }
            val values = mutableListOf<String>()
            ZipInputStream(Files.newInputStream(output)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    assertEquals("shared.txt", entry.name)
                    values.add(zip.readBytes().decodeToString())
                }
            }
            assertEquals(2, values.size)
            assertEquals(setOf("input-value", "generated-value"), values.toSet())
            ZipFile(output.toFile()).use { zip ->
                assertEquals(2, zip.entries().asSequence().count { it.name == "shared.txt" })
                assertEquals(values.last(), zip.getInputStream(zip.getEntry("shared.txt")).use { it.readBytes().decodeToString() })
            }
        }
    }

    @Test
    fun corruptCrcRetainsLegacyDirectoryOnlyCorruptionAndSeedSequence() = workspace { dir ->
        val input = dir.resolve("input.jar")
        writeZip(input, linkedMapOf(
            "assets/" to byteArrayOf(),
            "assets/value.txt" to "streamed-input".toByteArray(),
            "Sample.class" to sampleClass("Sample")
        ))
        WorkResources.read(input).use { resources ->
            resources.addGeneratedResource("generated.txt", "streamed-generated".toByteArray())
            val output = dir.resolve("corrupt.jar")
            val config = GlobalConfig(input = input.toString(), output = output.toString(), corruptCRC32 = true, dumpMappings = false)
            val instance = Grunteon(config, ObfuscationIO(PathResourceInput(input)), resources, emptyList())
            val expectedDirectoryCrc = context(instance) {
                Xoshiro256PPRandom(getSeed(config.input, output.fileName.toString(), "corruptCRC32"))
                    .nextInt(Int.MAX_VALUE - 1).toLong()
            }
            context(instance) { JarDumper.dumpJar(PathResourceOutput(output)) }
            ZipFile(output.toFile()).use { zip ->
                assertEquals(expectedDirectoryCrc, zip.getEntry("assets/").crc)
                for (entry in zip.entries().asSequence().filterNot { it.isDirectory }) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    assertEquals(CRC32().apply { update(bytes) }.value, entry.crc,
                        "Legacy file CRCs remain valid; only outer-stream directories consume corruption RNG: " + entry.name)
                }
                assertEquals("streamed-input", zip.getInputStream(zip.getEntry("assets/value.txt")).use { it.readBytes().decodeToString() })
                assertEquals("streamed-generated", zip.getInputStream(zip.getEntry("generated.txt")).use { it.readBytes().decodeToString() })
            }
        }
    }

    @Test
    fun directoryInputCannotStreamItsGrowingOutputThroughADelegatingSink() = workspace { dir ->
        val input = Files.createDirectory(dir.resolve("input"))
        val asset = kotlin.random.Random(912).nextBytes(1024 * 1024)
        Files.write(input.resolve("asset.bin"), asset)
        val target = input.resolve("output.jar")
        val delegate = PathResourceOutput(target)
        val written = AtomicInteger()
        val limit = 4 * 1024 * 1024
        val guarded = object : ResourceOutput by delegate {
            override fun targetPath(): Path = delegate.targetPath()
            override fun openOutputStream(): OutputStream = object : FilterOutputStream(delegate.openOutputStream()) {
                private fun reserve(bytes: Int) {
                    if (written.addAndGet(bytes) > limit) throw IOException("Output appears to be streaming itself")
                }
                override fun write(value: Int) { reserve(1); out.write(value) }
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    reserve(length)
                    out.write(bytes, offset, length)
                }
            }
        }
        assertEquals(target, guarded.targetPath())
        WorkResources.read(input).use { resources ->
            val config = GlobalConfig(input = input.toString(), output = target.toString(), compressionLevel = 0, dumpMappings = false)
            val instance = Grunteon(config, ObfuscationIO(PathResourceInput(input)), resources, emptyList())
            val worker = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "regression-output-identity").apply { isDaemon = true }
            }
            try {
                worker.submit { context(instance) { JarDumper.dumpJar(guarded) } }.get(10, TimeUnit.SECONDS)
            } finally {
                worker.shutdownNow()
                assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS))
            }
            assertTrue(written.get() < limit)
            assertEquals(written.get().toLong(), Files.size(target))
            ZipFile(target.toFile()).use { zip ->
                assertNull(zip.getEntry("output.jar"))
                assertEquals(setOf("asset.bin"), zip.entries().asSequence().map { it.name }.toSet())
                val entry = zip.getEntry("asset.bin")
                assertContentEquals(asset, zip.getInputStream(entry).use { it.readBytes() })
                assertEquals(CRC32().apply { update(asset) }.value, entry.crc)
            }
        }
    }

    @Test
    fun realPathResourceOutputInsideDirectoryInputDoesNotIncludeItself() = workspace { dir ->
        val input = Files.createDirectory(dir.resolve("input"))
        Files.writeString(input.resolve("asset.txt"), "small-resource")
        val target = input.resolve("output.jar")
        WorkResources.read(input).use { resources ->
            val output = PathResourceOutput(target)
            assertEquals(target, output.targetPath())
            val config = GlobalConfig(input = input.toString(), output = target.toString(), dumpMappings = false)
            val instance = Grunteon(config, ObfuscationIO(PathResourceInput(input)), resources, emptyList())
            context(instance) { JarDumper.dumpJar(output) }
            ZipFile(target.toFile()).use { zip ->
                assertNull(zip.getEntry("output.jar"))
                assertEquals(setOf("asset.txt"), zip.entries().asSequence().map { it.name }.toSet())
                assertEquals("small-resource", zip.getInputStream(zip.getEntry("asset.txt")).use { it.readBytes().decodeToString() })
            }
        }
    }

    @Test
    fun failedZipSinkClosesOutputAndCancelsBlockedBudgetWorkers() = workspace { dir ->
        val input = Files.createDirectory(dir.resolve("input"))
        Files.write(input.resolve("large.bin"), kotlin.random.Random(319).nextBytes(512 * 1024))
        WorkResources.read(input).use { resources ->
            repeat(24) { index ->
                resources.addGeneratedClass(ClassNode().apply { ClassReader(sampleClass("failure/C" + index)).accept(this, 0) })
            }
            val closed = AtomicBoolean()
            val output = object : ResourceOutput {
                override val description = "deliberately failing regression sink"
                override val fileName = "failure.jar"
                override fun exists() = false
                override fun openOutputStream() = object : OutputStream() {
                    override fun write(value: Int): Unit = throw IOException("regression sink failure")
                    override fun write(bytes: ByteArray, offset: Int, length: Int): Unit = throw IOException("regression sink failure")
                    override fun close() { closed.set(true) }
                }
            }
            val config = GlobalConfig(input = input.toString(), output = null, dumpMappings = false)
            val instance = Grunteon(config, ObfuscationIO(PathResourceInput(input)), resources, emptyList())
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "regression-failing-zip").apply { isDaemon = true }
            }
            val previousBudget = System.getProperty("grunteon.dump.maxInFlightBytes")
            System.setProperty("grunteon.dump.maxInFlightBytes", "1")
            try {
                val result = executor.submit { context(instance) { JarDumper.dumpJar(output) } }
                val failure = assertFailsWith<ExecutionException> { result.get(10, TimeUnit.SECONDS) }
                assertTrue(generateSequence(failure.cause) { it.cause }.any { it.message == "regression sink failure" })
                assertTrue(closed.get())
            } finally {
                executor.shutdownNow()
                if (previousBudget == null) System.clearProperty("grunteon.dump.maxInFlightBytes")
                else System.setProperty("grunteon.dump.maxInFlightBytes", previousBudget)
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Cancelled dump workers must not keep the sink alive")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun missingClasses(resources: WorkResources): Set<String> =
        WorkResources::class.java.getDeclaredField("missingClasses").apply { isAccessible = true }.get(resources) as Set<String>

    private fun node(name: String) = ClassNode().apply {
        version = V1_8
        access = ACC_PUBLIC
        this.name = name
        superName = "java/lang/Object"
    }

    private fun sampleClass(name: String): ByteArray {
        val node = node(name).apply {
            visibleAnnotations = mutableListOf(AnnotationNode("Lexample/Marker;"))
            fields.add(FieldNode(ACC_PUBLIC or ACC_STATIC or ACC_FINAL, "VALUE", "I", null, 23))
            methods.add(MethodNode(ACC_PUBLIC or ACC_STATIC, "value", "()I", null, null).apply {
                instructions.add(IntInsnNode(BIPUSH, 23))
                instructions.add(InsnNode(IRETURN))
            })
        }
        return ClassWriter(ClassWriter.COMPUTE_FRAMES).apply { node.accept(this) }.toByteArray()
    }

    private fun writeZip(path: Path, entries: Map<String, ByteArray>) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun checkCentralDirectoryOffsets(bytes: ByteArray, expectedNames: Set<String>) {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val end = (bytes.size - 22 downTo 0).first { buffer.getInt(it) == 0x06054b50 }
        var cursor = buffer.getInt(end + 16)
        val count = buffer.getShort(end + 10).toInt() and 65535
        val names = mutableSetOf<String>()
        repeat(count) {
            assertEquals(0x02014b50, buffer.getInt(cursor))
            val nameSize = buffer.getShort(cursor + 28).toInt() and 65535
            val extraSize = buffer.getShort(cursor + 30).toInt() and 65535
            val commentSize = buffer.getShort(cursor + 32).toInt() and 65535
            val offset = buffer.getInt(cursor + 42)
            val name = String(bytes, cursor + 46, nameSize, Charsets.UTF_8)
            assertEquals(0x04034b50, buffer.getInt(offset), name)
            val localNameSize = buffer.getShort(offset + 26).toInt() and 65535
            assertEquals(name, String(bytes, offset + 30, localNameSize, Charsets.UTF_8))
            names.add(name)
            cursor += 46 + nameSize + extraSize + commentSize
        }
        assertEquals(expectedNames, names)
    }

    private fun workspace(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("grunt-resource-regression")
        try { block(dir) } finally {
            Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }
}
