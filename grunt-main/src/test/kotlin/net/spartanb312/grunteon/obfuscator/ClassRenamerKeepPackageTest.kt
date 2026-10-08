package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerEntry
import net.spartanb312.grunteon.obfuscator.process.transformers.other.ReflectionSupport
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.ClassRenamer
import net.spartanb312.grunteon.testcase.classrename.keeppackage.PackageAccess
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassRenamerKeepPackageTest {
    private val pkg = "net/spartanb312/grunteon/testcase/classrename/keeppackage/"
    private val target = pkg + "PackageAccess"
    private val peer = pkg + "a"
    private val filter = ClassFilterConfig(includeStrategy = listOf(target), excludeStrategy = emptyList())

    @Test
    fun existingParentSettingKeepsPackageAndAccessWhenNamesDoNotCollide() {
        val instance = instance(ClassRenamer.Config(classFilter = filter, parent = pkg, prefix = "renamed_"))
        instance.run()
        assertEquals(pkg + "renamed_a", instance.nameMapping.getMapping(target))
        assertPeerAndExecute(instance)
    }

    @Test
    fun keptPackageDoesNotOverwriteExcludedPeer() {
        val instance = instance(ClassRenamer.Config(classFilter = filter, parent = pkg))
        val inputCount = instance.workRes.inputClassMap.size
        instance.run()
        assertNotEquals(peer, instance.nameMapping.getMapping(target))
        assertEquals(inputCount, instance.workRes.inputClassMap.size)
        assertPeerAndExecute(instance)
    }

    @Test
    fun keptPackageAvoidsLibraryAndEarlierMappingTargets() {
        val added = pkg + "Added"
        val instance = instance(
            ClassRenamer.Config(classFilter = filter, parent = pkg),
            ClassRenamer.Config(
                classFilter = ClassFilterConfig(includeStrategy = listOf(added), excludeStrategy = emptyList()),
                parent = pkg
            )
        )
        instance.workRes.libraryClassMap[pkg + "b"] = emptyClass(pkg + "b")
        instance.workRes.addGeneratedClass(emptyClass(added))
        val inputCount = instance.workRes.inputClassMap.size
        instance.run()
        val first = assertNotNull(instance.nameMapping.getMapping(target))
        val second = assertNotNull(instance.nameMapping.getMapping(added))
        assertNotEquals(first, second)
        assertTrue(first !in setOf(peer, pkg + "b"))
        assertTrue(second !in setOf(peer, pkg + "b"))
        assertEquals(inputCount, instance.workRes.inputClassMap.size)
        assertEquals(target, instance.nameMapping.getOriginalClassName(first))
        assertEquals(added, instance.nameMapping.getOriginalClassName(second))
        assertPeerAndExecute(instance)
    }

    @Test
    fun legacyDefaultTargetAndConfigRoundTripRemainUnchanged() {
        val config = ClassRenamer.Config(classFilter = filter)
        val path = Files.createTempFile("class-renamer-keep-package", ".json")
        try {
            val original = ObfConfig(transformers = listOf(TransformerEntry(config = config)))
            ObfConfig.write(original, path)
            assertEquals(original, ObfConfig.read(path))
        } finally {
            path.deleteIfExists()
        }
        val instance = instance(config)
        instance.run()
        assertEquals("net/spartanb312/obf/a", instance.nameMapping.getMapping(target))
        assertNull(instance.nameMapping.getMapping(peer))
    }

    private fun instance(vararg configs: ClassRenamer.Config): Grunteon = readTestClasses(
        PackageAccess::class.java,
        ObfConfig(
            globalConfig = GlobalConfig(output = null, dumpMappings = false, exclusions = listOf(peer)),
            transformers = listOf(TransformerEntry(config = ReflectionSupport.Config(classFilter = filter))) +
                    configs.map { TransformerEntry(config = it) }
        )
    )

    private fun assertPeerAndExecute(instance: Grunteon) {
        val renamed = assertNotNull(instance.nameMapping.getMapping(target))
        assertEquals(pkg, renamed.substringBeforeLast('/') + "/")
        assertNull(instance.nameMapping.getMapping(peer))
        val excluded = assertNotNull(instance.workRes.inputClassMap[peer])
        assertEquals("(L$renamed;)L$renamed;", excluded.methods.single { it.name == "echo" }.desc)
        val directory = Files.createTempDirectory("class-renamer-keep-package-")
        try {
            instance.workRes.inputClassCollection.filter { it.name.startsWith(pkg) }.forEach { clazz ->
                val writer = ClassWriter(0)
                clazz.accept(writer)
                val file = directory.resolve(clazz.name + ".class")
                Files.createDirectories(file.parent)
                Files.write(file, writer.toByteArray())
            }
            val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")
            val java = Path.of(System.getProperty("java.home"), "bin", if (isWindows) "java.exe" else "java")
            val process = ProcessBuilder(java.toString(), "-Xverify:all", "-cp", directory.toString(), renamed.replace('/', '.'))
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), output)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun emptyClass(className: String) = ClassNode().apply {
        version = Opcodes.V1_8
        access = Opcodes.ACC_PUBLIC
        name = className
        superName = "java/lang/Object"
    }
}
