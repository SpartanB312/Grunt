package net.spartanb312.grunteon.ui

import kotlinx.coroutines.*
import net.spartanb312.grunteon.obfuscator.lang.I18n
import net.spartanb312.grunteon.obfuscator.lang.Language
import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerEntry
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class AppModelIoTest {
    /** A memory store also asserts that all storage calls are outside the model/UI caller thread. */
    private open class MemoryStorage : UiConfigStorage() {
        val uiThread = Thread.currentThread()
        var config = ObfConfig()
        var settings = AppConfig()
        var state = AppState()
        var readCount = 0
        val savedConfigs = mutableListOf<ObfConfig>()
        val savedSettings = mutableListOf<AppConfig>()
        val savedStates = mutableListOf<AppState>()
        fun checkIoThread() { assertNotSame(uiThread, Thread.currentThread()) }
        override fun readAppConfig(path: Path): AppConfig { checkIoThread(); readCount++; return settings }
        override fun readAppState(path: Path): AppState { checkIoThread(); readCount++; return state }
        override fun readConfig(path: Path): ObfConfig { checkIoThread(); readCount++; return config }
        override fun writeAppConfig(config: AppConfig, path: Path) { checkIoThread(); savedSettings += config }
        override fun writeAppState(state: AppState, path: Path) { checkIoThread(); savedStates += state }
        override fun writeConfig(config: ObfConfig, path: Path) { checkIoThread(); savedConfigs += config }
    }

    private fun withModel(
        storage: MemoryStorage,
        exit: () -> Unit = {},
        block: suspend CoroutineScope.(AppModel, Path) -> Unit,
    ) = runBlocking {
        val directory = Files.createTempDirectory("grunteon-ui-model-test-")
        val queue = ConfigIoQueue()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val model = AppModel(exit, scope, directory, storage, queue)
        try {
            withTimeout(10_000) { block(model, directory) }
        } finally {
            scope.cancel()
            queue.close()
            directory.toFile().deleteRecursively()
        }
    }

    @AfterTest
    fun resetLanguage() {
        I18n.setLanguage(Language.English)
        I18n.clearCatalogsForTesting()
    }

    @Test
    fun startupRunsAfterConstructionAndDoesNotOverwriteANewDocument() {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val storage = object : MemoryStorage() {
            override fun readAppConfig(path: Path): AppConfig {
                started.complete(Unit)
                check(release.await(5, TimeUnit.SECONDS))
                return super.readAppConfig(path)
            }
        }
        try {
            withModel(storage) { model, _ ->
                assertEquals(0, storage.readCount)
                val startup = launch { model.initialize() }
                started.await()
                model.newConfig()
                model.obfConfig = model.obfConfig.copy(globalConfig = GlobalConfig(output = "new.jar"))
                release.countDown()
                startup.join()
                assertEquals("new.jar", model.obfConfig.globalConfig.output)
                assertTrue(model.hasUnsavedChanges)
                assertNull(model.appState.configPath)
            }
        } finally { release.countDown() }
    }

    @Test
    fun documentCreatedBeforeInitializationIsNotReplacedByLastConfig() {
        val storage = MemoryStorage().apply { state = AppState(Path.of("old.json")) }
        withModel(storage) { model, _ ->
            model.newConfig()
            model.obfConfig = ObfConfig(globalConfig = GlobalConfig(baseSeed = "already-edited"))
            model.initialize()
            assertEquals("already-edited", model.obfConfig.globalConfig.baseSeed)
            assertNull(model.appState.configPath)
            assertTrue(model.hasUnsavedChanges)
            assertEquals(1, storage.readCount) // Settings only; no old document or path load.
        }
    }

    @Test
    fun loadedSettingsLanguageAndLastConfigSurviveInitialization() {
        val storage = MemoryStorage().apply {
            settings = AppConfig(language = Language.ChineseCN)
            state = AppState(Path.of("last.json"))
            config = ObfConfig(globalConfig = GlobalConfig(baseSeed = "saved-seed"))
        }
        withModel(storage) { model, _ ->
            model.initialize()
            model.initialize()
            assertEquals(3, storage.readCount)
            assertEquals(storage.settings, model.appConfig)
            assertEquals(Language.ChineseCN, I18n.currentLanguage)
            assertTrue(model.languageRevision > 0)
            assertEquals("saved-seed", model.obfConfig.globalConfig.baseSeed)
            assertEquals(storage.state, model.appState)
            assertFalse(model.hasUnsavedChanges)
        }
    }

    @Test
    fun lateOpenCannotOverwriteEditsAndTheNewestOpenWins() {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val olderStarted = CompletableDeferred<Unit>()
        val olderRelease = CountDownLatch(1)
        val storage = object : MemoryStorage() {
            override fun readConfig(path: Path): ObfConfig {
                if (path.fileName.toString() == "first.json") {
                    started.complete(Unit)
                    check(release.await(5, TimeUnit.SECONDS))
                }
                if (path.fileName.toString() == "older.json") {
                    olderStarted.complete(Unit)
                    check(olderRelease.await(5, TimeUnit.SECONDS))
                }
                checkIoThread()
                return ObfConfig(globalConfig = GlobalConfig(baseSeed = path.fileName.toString()))
            }
        }
        try {
            withModel(storage) { model, directory ->
                val first = async { model.openConfig(directory.resolve("first.json")) }
                started.await()
                model.obfConfig = model.obfConfig.copy(globalConfig = GlobalConfig(baseSeed = "edited"))
                release.countDown()
                assertFalse(first.await())
                assertEquals("edited", model.obfConfig.globalConfig.baseSeed)
                assertTrue(model.hasUnsavedChanges)
                val older = async(start = CoroutineStart.UNDISPATCHED) { model.openConfig(directory.resolve("older.json")) }
                olderStarted.await()
                val newest = async(start = CoroutineStart.UNDISPATCHED) { model.openConfig(directory.resolve("newest.json")) }
                olderRelease.countDown()
                assertFalse(older.await())
                assertTrue(newest.await())
                assertEquals("newest.json", model.obfConfig.globalConfig.baseSeed)
                assertFalse(model.hasUnsavedChanges)
            }
        } finally {
            release.countDown()
            olderRelease.countDown()
        }
    }

    @Test
    fun pendingOpenCannotReplaceInPlaceListEdits() {
        val libraries = mutableListOf("original.jar")
        val readStarted = CompletableDeferred<Unit>()
        val releaseRead = CountDownLatch(1)
        val storage = object : MemoryStorage() {
            override fun readConfig(path: Path): ObfConfig {
                if (path.fileName.toString() == "replacement.json") {
                    checkIoThread()
                    readStarted.complete(Unit)
                    check(releaseRead.await(5, TimeUnit.SECONDS))
                    return ObfConfig(globalConfig = GlobalConfig(baseSeed = "replacement"))
                }
                return super.readConfig(path)
            }
        }.apply {
            config = ObfConfig(globalConfig = GlobalConfig(libs = libraries))
            state = AppState(Path.of("original.json"))
        }
        try {
            withModel(storage) { model, directory ->
                model.initialize()
                assertFalse(model.hasUnsavedChanges)
                val open = async { model.openConfig(directory.resolve("replacement.json")) }
                readStarted.await()
                libraries += "later.jar"
                releaseRead.countDown()
                assertFalse(open.await())
                assertTrue(model.hasUnsavedChanges)
                assertEquals(listOf("original.jar", "later.jar"), model.obfConfig.globalConfig.libs)
                assertEquals(Path.of("original.json"), model.appState.configPath)
            }
        } finally { releaseRead.countDown() }
    }

    @Test
    fun saveDetachesSnapshotAndKeepsInPlaceListChangesDuringWriteDirty() {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val storage = object : MemoryStorage() {
            override fun writeConfig(config: ObfConfig, path: Path) {
                started.complete(Unit)
                check(release.await(5, TimeUnit.SECONDS))
                super.writeConfig(config, path)
            }
        }
        try {
            withModel(storage) { model, directory ->
                model.initialize()
                val libraries = mutableListOf("original.jar")
                model.obfConfig = ObfConfig(globalConfig = GlobalConfig(libs = libraries, baseSeed = "before"))
                val save = async { model.saveConfig(directory.resolve("saved.json")) }
                started.await()
                libraries += "later.jar"
                release.countDown()
                assertFalse(save.await()) // A pending save-and-exit must not discard the later edit.
                assertTrue(model.hasUnsavedChanges)
                assertEquals(listOf("original.jar"), storage.savedConfigs.single().globalConfig.libs)
                assertEquals("before", storage.savedConfigs.single().globalConfig.baseSeed)
                assertEquals("before", model.obfConfig.globalConfig.baseSeed)
                assertEquals(listOf("original.jar", "later.jar"), model.obfConfig.globalConfig.libs)
            }
        } finally { release.countDown() }
    }

    @Test
    fun oldSaveDoesNotSelectOrCleanANewDocument() {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val storage = object : MemoryStorage() {
            override fun writeConfig(config: ObfConfig, path: Path) {
                started.complete(Unit)
                check(release.await(5, TimeUnit.SECONDS))
                super.writeConfig(config, path)
            }
        }
        try {
            withModel(storage) { model, directory ->
                model.initialize()
                val save = async { model.saveConfig(directory.resolve("old.json")) }
                started.await()
                model.newConfig()
                model.obfConfig = model.obfConfig.copy(globalConfig = GlobalConfig(baseSeed = "new"))
                release.countDown()
                assertFalse(save.await())
                assertNull(model.appState.configPath)
                assertTrue(model.hasUnsavedChanges)
            }
        } finally { release.countDown() }
    }

    data class ArrayBodyConfig(
        val bytes: ByteArray = byteArrayOf(1),
        val nested: Array<IntArray> = arrayOf(intArrayOf(2)),
    ) : TransformerConfig() {
        val names = mutableListOf("original")
    }

    @Test
    fun saveCommandsAreRejectedDuringInitializationWithoutWritingDefaults() {
        val storage = MemoryStorage()
        withModel(storage) { model, directory ->
            assertFalse(model.configCommandsEnabled)
            assertNull(model.beginConfigSave())
            assertFalse(model.saveConfig(directory.resolve("must-not-write.json")))
            assertTrue(storage.savedConfigs.isEmpty())
            model.initialize()
            assertTrue(model.configCommandsEnabled)
        }
    }

    @Test
    fun pendingOpenCompletingDuringSaveChooserCannotWriteAnotherDocumentOrContinue() {
        val readStarted = CompletableDeferred<Unit>()
        val releaseRead = CountDownLatch(1)
        val storage = object : MemoryStorage() {
            override fun readConfig(path: Path): ObfConfig {
                readStarted.complete(Unit)
                check(releaseRead.await(5, TimeUnit.SECONDS))
                checkIoThread()
                return ObfConfig(globalConfig = GlobalConfig(baseSeed = "opened-A"))
            }
        }
        try {
            withModel(storage) { model, directory ->
                model.initialize()
                model.obfConfig = ObfConfig(globalConfig = GlobalConfig(baseSeed = "visible-B"))
                val open = async { model.openConfig(directory.resolve("A.json")) }
                readStarted.await()
                val intent = assertNotNull(model.beginConfigSave())
                val chosenPath = CompletableDeferred<Path?>()
                var continued = false
                val save = async(start = CoroutineStart.UNDISPATCHED) {
                    model.saveConfig(intent) { chosenPath.await() }.also { if (it) continued = true }
                }
                releaseRead.countDown()
                assertTrue(open.await())
                chosenPath.complete(directory.resolve("B.json"))
                assertFalse(save.await())
                assertFalse(continued)
                assertTrue(storage.savedConfigs.isEmpty())
                assertEquals("opened-A", model.obfConfig.globalConfig.baseSeed)
                assertEquals(directory.resolve("A.json"), model.appState.configPath)
                assertFalse(model.hasUnsavedChanges)
            }
        } finally { releaseRead.countDown() }
    }

    @Test
    fun newOpenRequestDuringChooserRejectsSaveBeforeTheReadCompletes() {
        val readStarted = CompletableDeferred<Unit>()
        val releaseRead = CountDownLatch(1)
        val storage = object : MemoryStorage() {
            override fun readConfig(path: Path): ObfConfig {
                readStarted.complete(Unit)
                check(releaseRead.await(5, TimeUnit.SECONDS))
                return super.readConfig(path)
            }
        }
        try {
            withModel(storage) { model, directory ->
                model.initialize()
                val intent = assertNotNull(model.beginConfigSave())
                val chosenPath = CompletableDeferred<Path?>()
                val save = async(start = CoroutineStart.UNDISPATCHED) {
                    model.saveConfig(intent) { chosenPath.await() }
                }
                val open = async { model.openConfig(directory.resolve("A.json")) }
                readStarted.await()
                chosenPath.complete(directory.resolve("B.json"))
                assertFalse(withTimeout(1_000) { save.await() }) // Must not enqueue behind the blocked read.
                assertTrue(storage.savedConfigs.isEmpty())
                releaseRead.countDown()
                assertTrue(open.await())
            }
        } finally { releaseRead.countDown() }
    }

    @Test
    fun inPlaceListEditWhileChooserIsSuspendedRejectsWriteAndMarksDocumentDirty() {
        val libraries = mutableListOf("original.jar")
        val storage = MemoryStorage().apply {
            config = ObfConfig(globalConfig = GlobalConfig(libs = libraries))
            state = AppState(Path.of("original.json"))
        }
        withModel(storage) { model, directory ->
            model.initialize()
            assertFalse(model.hasUnsavedChanges)
            val intent = assertNotNull(model.beginConfigSave())
            val chosenPath = CompletableDeferred<Path?>()
            val save = async(start = CoroutineStart.UNDISPATCHED) {
                model.saveConfig(intent) { chosenPath.await() }
            }
            libraries += "later.jar" // No setter or revision increment.
            chosenPath.complete(directory.resolve("B.json"))
            assertFalse(save.await())
            assertTrue(storage.savedConfigs.isEmpty())
            assertTrue(model.hasUnsavedChanges)
            assertEquals(Path.of("original.json"), model.appState.configPath)
        }
    }

    @Test
    fun saveContinuationRunsBeforeQueuedConsumerAndCannotDiscardLaterEdits() {
        val storage = MemoryStorage()
        withModel(storage) { model, directory ->
            model.initialize()
            model.obfConfig = ObfConfig(globalConfig = GlobalConfig(baseSeed = "saved"))
            val intent = assertNotNull(model.beginConfigSave())
            val consumerQueued = CompletableDeferred<Unit>()
            val releaseConsumer = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            lateinit var saving: Deferred<Boolean>
            saving = async(start = CoroutineStart.LAZY) {
                model.saveConfig(intent, onSaved = {
                    assertSame(storage.uiThread, Thread.currentThread())
                    assertFalse(saving.isCompleted)
                    events += "continued"
                    model.newConfig() // The original save-and-continue action.
                }) { directory.resolve("saved.json") }
            }
            val consumer = launch(start = CoroutineStart.UNDISPATCHED) {
                assertTrue(saving.await())
                consumerQueued.complete(Unit)
                releaseConsumer.await() // Deterministically delay the toolbar's result consumer.
                events += "consumed"
            }
            consumerQueued.await()
            assertEquals(listOf("continued"), events)
            assertFalse(model.hasUnsavedChanges)
            model.obfConfig = ObfConfig(globalConfig = GlobalConfig(baseSeed = "later-edit"))
            releaseConsumer.complete(Unit)
            consumer.join()
            assertEquals(listOf("continued", "consumed"), events)
            assertEquals("saved", storage.savedConfigs.single().globalConfig.baseSeed)
            assertEquals("later-edit", model.obfConfig.globalConfig.baseSeed)
            assertTrue(model.hasUnsavedChanges)
        }
    }

    @Test
    fun unchangedClonedArraysCanBeSavedAfterChooserButIntentCannotBeReused() {
        val value = ArrayBodyConfig()
        val storage = MemoryStorage().apply {
            config = ObfConfig(transformers = listOf(TransformerEntry(config = value)))
            state = AppState(Path.of("original.json"))
        }
        withModel(storage) { model, directory ->
            model.initialize()
            val intent = assertNotNull(model.beginConfigSave())
            val chosenPath = CompletableDeferred<Path?>()
            val save = async(start = CoroutineStart.UNDISPATCHED) {
                model.saveConfig(intent) { originalPath ->
                    assertEquals(Path.of("original.json"), originalPath)
                    chosenPath.await()
                }
            }
            chosenPath.complete(directory.resolve("B.json"))
            assertTrue(save.await())
            val saved = storage.savedConfigs.single().transformers.single().config as ArrayBodyConfig
            assertNotSame(value.bytes, saved.bytes)
            assertNotSame(value.nested[0], saved.nested[0])
            assertContentEquals(value.bytes, saved.bytes)
            assertContentEquals(value.nested[0], saved.nested[0])
            assertEquals(value.names, saved.names)
            assertFalse(model.hasUnsavedChanges)
            var pickerCalled = false
            assertFalse(model.saveConfig(intent) { pickerCalled = true; directory.resolve("again.json") })
            assertFalse(pickerCalled)
            assertEquals(1, storage.savedConfigs.size)
        }
    }

    @Test
    fun arrayAndBodyEditsDuringWriteCannotBeMarkedSaved() {
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CountDownLatch(1)
        val value = ArrayBodyConfig()
        val storage = object : MemoryStorage() {
            override fun writeConfig(config: ObfConfig, path: Path) {
                writeStarted.complete(Unit)
                check(releaseWrite.await(5, TimeUnit.SECONDS))
                super.writeConfig(config, path)
            }
        }.apply {
            config = ObfConfig(transformers = listOf(TransformerEntry(config = value)))
            state = AppState(Path.of("original.json"))
        }
        try {
            withModel(storage) { model, directory ->
                model.initialize()
                assertFalse(model.hasUnsavedChanges)
                val save = async { model.saveConfig(directory.resolve("B.json")) }
                writeStarted.await()
                value.nested[0][0] = 9
                value.names += "later"
                releaseWrite.countDown()
                assertFalse(save.await())
                assertTrue(model.hasUnsavedChanges)
                val saved = storage.savedConfigs.single().transformers.single().config as ArrayBodyConfig
                assertEquals(2, saved.nested[0][0])
                assertEquals(listOf("original"), saved.names)
            }
        } finally { releaseWrite.countDown() }
    }

    @Test
    fun newDocumentOrPathChangeWhileChooserIsPendingRejectsBeforeWrite() {
        val changes: List<(AppModel) -> Unit> = listOf(
            { it.newConfig() },
            { it.appState = it.appState.copy(configPath = Path.of("other.json")) },
        )
        for (change in changes) {
            val storage = MemoryStorage()
            withModel(storage) { model, directory ->
                model.initialize()
                val intent = assertNotNull(model.beginConfigSave())
                val chosenPath = CompletableDeferred<Path?>()
                val save = async(start = CoroutineStart.UNDISPATCHED) {
                    model.saveConfig(intent) { chosenPath.await() }
                }
                change(model)
                chosenPath.complete(directory.resolve("stale.json"))
                assertFalse(save.await())
                assertTrue(storage.savedConfigs.isEmpty())
            }
        }
    }

    @Test
    fun closingDuringChooserRejectsThePendingAndNewSaveCommands() {
        val flushStarted = CompletableDeferred<Unit>()
        val exited = CompletableDeferred<Unit>()
        val releaseFlush = CountDownLatch(1)
        val storage = MemoryStorage()
        try {
            withModel(storage, exit = { exited.complete(Unit) }) { model, directory ->
                model.initialize()
                model.beforeExitIo = {
                    flushStarted.complete(Unit)
                    check(releaseFlush.await(5, TimeUnit.SECONDS))
                }
                val intent = assertNotNull(model.beginConfigSave())
                val chosenPath = CompletableDeferred<Path?>()
                val save = async(start = CoroutineStart.UNDISPATCHED) {
                    model.saveConfig(intent) { chosenPath.await() }
                }
                model.onExit()
                flushStarted.await()
                assertFalse(model.configCommandsEnabled)
                assertNull(model.beginConfigSave())
                chosenPath.complete(directory.resolve("B.json"))
                assertFalse(save.await())
                assertFalse(model.saveConfig(directory.resolve("C.json")))
                assertTrue(storage.savedConfigs.isEmpty())
                releaseFlush.countDown()
                exited.await()
            }
        } finally { releaseFlush.countDown() }
    }

    @Test
    fun exitDuringStartupDoesNotOverwriteUnloadedSettingsWithDefaults() {
        val exited = CompletableDeferred<Unit>()
        val storage = MemoryStorage().apply { settings = AppConfig(uiLogLevel = UiLogLevel.Debug) }
        withModel(storage, exit = { exited.complete(Unit) }) { model, _ ->
            assertTrue(model.isInitializing)
            model.onExit()
            exited.await()
            assertEquals(storage.settings, storage.savedSettings.last())
            assertFalse(model.isInitializing)
        }
    }

    @Test
    fun settingsChangedDuringLogFlushArePersistedBeforeExit() {
        val started = CompletableDeferred<Unit>()
        val exited = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val storage = MemoryStorage()
        try {
            withModel(storage, exit = { exited.complete(Unit) }) { model, _ ->
                model.beforeExitIo = {
                    started.complete(Unit)
                    check(release.await(5, TimeUnit.SECONDS))
                }
                model.onExit()
                started.await()
                model.appConfig = model.appConfig.copy(uiLogLevel = UiLogLevel.Debug)
                release.countDown()
                exited.await()
                assertEquals(UiLogLevel.Debug, storage.savedSettings.last().uiLogLevel)
            }
        } finally { release.countDown() }
    }

    @Test
    fun exitWaitsForWritesAndFlushesTheLatestSettingsRevision() {
        val started = CompletableDeferred<Unit>()
        val exited = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        var logFlushed = false
        val storage = object : MemoryStorage() {
            override fun writeAppConfig(config: AppConfig, path: Path) {
                if (savedSettings.isEmpty()) {
                    started.complete(Unit)
                    check(release.await(5, TimeUnit.SECONDS))
                }
                super.writeAppConfig(config, path)
            }
        }
        try {
            withModel(storage, exit = { exited.complete(Unit) }) { model, _ ->
                model.beforeExitIo = { storage.checkIoThread(); logFlushed = true }
                model.onExit()
                started.await()
                assertFalse(exited.isCompleted)
                model.appConfig = model.appConfig.copy(uiLogLevel = UiLogLevel.Debug)
                release.countDown()
                exited.await()
                assertEquals(UiLogLevel.Debug, storage.savedSettings.last().uiLogLevel)
                assertTrue(storage.savedStates.isNotEmpty())
                assertTrue(logFlushed)
            }
        } finally { release.countDown() }
    }
}
