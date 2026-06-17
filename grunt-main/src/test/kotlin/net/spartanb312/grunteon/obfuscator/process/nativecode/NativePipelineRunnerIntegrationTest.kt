package net.spartanb312.grunteon.obfuscator.process.nativecode

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.util.NATIVE_INCLUDED
import net.spartanb312.grunteon.obfuscator.util.extensions.appendAnnotation
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.VarInsnNode
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativePipelineRunnerIntegrationTest {

    @Test
    fun runnerCommitsBytecodeAndResourceOnlyAfterSuccessfulCompile() {
        val method = intHelper("nativeAdd").appendAnnotation(NATIVE_INCLUDED)
        val instance = instanceWith(classNode("test/NativePipelineCommit", method))
        val workDir = createTempDirectory("grunteon-native-work")
        val compiler = fakeCompiler(workDir, succeeds = true)

        context(instance) {
            NativePipelineRunner.run(
                NativePipelineConfig(
                    enabled = true,
                    workDir = workDir.pathString,
                    compilerExecutable = compiler.pathString,
                    failOnCompileError = true,
                    failOnValidationError = true,
                    cleanNativeAnnotations = true
                )
            )
        }

        val transformedClass = instance.workRes.inputClassMap.getValue("test/NativePipelineCommit")
        val transformedMethod = transformedClass.methods.single { it.name == "nativeAdd" }
        assertTrue(transformedMethod.access and Opcodes.ACC_NATIVE != 0)
        assertEquals(0, transformedMethod.instructions.size())
        assertTrue(transformedMethod.annotationDescs().isEmpty())

        val clinit = transformedClass.methods.single { it.name == "<clinit>" && it.desc == "()V" }
        val clinitCalls = clinit.instructions.toArray().filterIsInstance<MethodInsnNode>()
        assertTrue(clinitCalls.any { it.name == "load" })
        assertTrue(clinitCalls.any { it.name == "registerNativesForClass" })

        assertTrue(instance.workRes.inputClassMap.keys.any {
            it.startsWith("net/spartanb312/grunteon/runtime/NativeLoader")
        })
        val resource = instance.workRes.generatedResources.entries.single {
            it.key.startsWith("grunteon/native/")
        }
        assertTrue(String(resource.value).contains("native-test"))
    }

    @Test
    fun runnerLeavesJavaBytecodeUnchangedWhenCompileFails() {
        val method = intHelper("nativeAdd").appendAnnotation(NATIVE_INCLUDED)
        val instance = instanceWith(classNode("test/NativePipelineFallback", method))
        val workDir = createTempDirectory("grunteon-native-work")
        val compiler = fakeCompiler(workDir, succeeds = false)

        val originalMethod = instance.workRes.inputClassMap
            .getValue("test/NativePipelineFallback")
            .methods
            .single { it.name == "nativeAdd" }
        val originalInstructionCount = originalMethod.instructions.size()

        context(instance) {
            NativePipelineRunner.run(
                NativePipelineConfig(
                    enabled = true,
                    workDir = workDir.pathString,
                    compilerExecutable = compiler.pathString,
                    failOnCompileError = false,
                    failOnValidationError = true,
                    cleanNativeAnnotations = true
                )
            )
        }

        val transformedClass = instance.workRes.inputClassMap.getValue("test/NativePipelineFallback")
        val transformedMethod = transformedClass.methods.single { it.name == "nativeAdd" }
        assertFalse(transformedMethod.access and Opcodes.ACC_NATIVE != 0)
        assertEquals(originalInstructionCount, transformedMethod.instructions.size())
        assertTrue(normalizeNativeAnnotation(NATIVE_INCLUDED) in transformedMethod.annotationDescs())
        assertFalse(transformedClass.methods.any { it.name == "<clinit>" })
        assertTrue(instance.workRes.generatedResources.isEmpty())
        assertFalse(instance.workRes.inputClassMap.keys.any {
            it.startsWith("net/spartanb312/grunteon/runtime/NativeLoader")
        })
    }

    @Test
    fun runnerCompiledNativeLibraryCanBeLoadedAndInvokedWhenToolchainExists() {
        val compiler = findHostCompiler()
        if (compiler == null) {
            println("Skipping native load E2E: no C++ compiler found on PATH")
            return
        }
        val includeRoot = resolveJniIncludeRoot(Path.of(System.getProperty("java.home")))
        val includeOs = includeRoot.resolve(NativePlatform.current().jniIncludeOs)
        if (!includeRoot.resolve("jni.h").exists() || !includeOs.exists()) {
            println("Skipping native load E2E: JNI headers not found under $includeRoot and $includeOs")
            return
        }

        val method = intHelper("nativeAdd").appendAnnotation(NATIVE_INCLUDED)
        val instance = instanceWith(classNode("test/NativePipelineReal", method))
        val workDir = createTempDirectory("grunteon-native-real")

        context(instance) {
            NativePipelineRunner.run(
                NativePipelineConfig(
                    enabled = true,
                    workDir = workDir.pathString,
                    compilerExecutable = compiler.absolutePath,
                    failOnCompileError = true,
                    failOnValidationError = true,
                    cleanNativeAnnotations = true
                )
            )
        }

        val classes = instance.workRes.inputClassMap.values.associate { classNode ->
            classNode.name.replace('/', '.') to classNode.toBytes()
        }
        val loader = NativeE2EClassLoader(
            parent = javaClass.classLoader,
            classes = classes,
            resources = instance.workRes.generatedResources.toMap()
        )
        val loadedClass = Class.forName("test.NativePipelineReal", true, loader)
        val nativeAdd = loadedClass.getDeclaredMethod(
            "nativeAdd",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )

        assertEquals(5, nativeAdd.invoke(null, 2, 3))
    }

    @Test
    fun executeDumpsRunnableJarWithPackagedNativeLibraryWhenToolchainExists() {
        val compiler = findHostCompiler()
        if (compiler == null) {
            println("Skipping native jar E2E: no C++ compiler found on PATH")
            return
        }
        val includeRoot = resolveJniIncludeRoot(Path.of(System.getProperty("java.home")))
        val includeOs = includeRoot.resolve(NativePlatform.current().jniIncludeOs)
        if (!includeRoot.resolve("jni.h").exists() || !includeOs.exists()) {
            println("Skipping native jar E2E: JNI headers not found under $includeRoot and $includeOs")
            return
        }

        val inputDir = createTempDirectory("grunteon-native-jar-input")
        val outputJar = createTempFile("grunteon-native-jar-output", ".jar")
        writeClass(
            inputDir,
            classNode(
                "test/NativePipelineJar",
                mainMethod(),
                intHelper("nativeAdd").appendAnnotation(NATIVE_INCLUDED)
            )
        )

        val instance = Grunteon.create(
            ObfConfig(
                globalConfig = GlobalConfig(
                    input = inputDir.pathString,
                    output = outputJar.pathString,
                    dumpMappings = false,
                    exclusions = emptyList(),
                    mixinExclusions = emptyList(),
                    missingCheck = false
                ),
                nativePipeline = NativePipelineConfig(
                    enabled = true,
                    workDir = createTempDirectory("grunteon-native-jar-work").pathString,
                    compilerExecutable = compiler.absolutePath,
                    failOnCompileError = true,
                    failOnValidationError = true,
                    cleanNativeAnnotations = true
                )
            )
        )
        instance.execute()

        JarFile(outputJar.toFile()).use { jar ->
            assertNotNull(jar.getEntry("test/NativePipelineJar.class"))
            assertTrue(jar.entries().asSequence().any { it.name.startsWith("grunteon/native/") })
            assertTrue(jar.entries().asSequence().any {
                it.name.startsWith("net/spartanb312/grunteon/runtime/NativeLoader") &&
                    it.name.endsWith(".class")
            })
        }

        val javaHome = Path.of(System.getProperty("java.home"))
        val javaExe = javaHome.resolve("bin").resolve(if (File.separatorChar == '\\') "java.exe" else "java")
        val process = ProcessBuilder(
            javaExe.absolutePathString(),
            "-cp",
            outputJar.absolutePathString(),
            "test.NativePipelineJar"
        ).redirectErrorStream(true)
            .apply {
                val oldPath = environment()["PATH"].orEmpty()
                environment()["PATH"] = compiler.parentFile.absolutePath + File.pathSeparator + oldPath
            }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        assertEquals(0, exitCode, "Dumped native jar failed with exit code $exitCode:\n$output")
        assertTrue("OK" in output)
    }

    @Test
    fun executeDumpsRunnableJarWithFullJvmNativeFeaturesWhenToolchainExists() {
        val compiler = findHostCompiler()
        if (compiler == null) {
            println("Skipping full JVM native jar E2E: no C++ compiler found on PATH")
            return
        }
        val includeRoot = resolveJniIncludeRoot(Path.of(System.getProperty("java.home")))
        val includeOs = includeRoot.resolve(NativePlatform.current().jniIncludeOs)
        if (!includeRoot.resolve("jni.h").exists() || !includeOs.exists()) {
            println("Skipping full JVM native jar E2E: JNI headers not found under $includeRoot and $includeOs")
            return
        }

        val inputDir = createTempDirectory("grunteon-native-full-jvm-input")
        val outputJar = createTempFile("grunteon-native-full-jvm-output", ".jar")
        writeClass(inputDir, fullJvmFeatureClass())

        val instance = Grunteon.create(
            ObfConfig(
                globalConfig = GlobalConfig(
                    input = inputDir.pathString,
                    output = outputJar.pathString,
                    dumpMappings = false,
                    exclusions = emptyList(),
                    mixinExclusions = emptyList(),
                    missingCheck = false
                ),
                nativePipeline = NativePipelineConfig(
                    enabled = true,
                    workDir = createTempDirectory("grunteon-native-full-jvm-work").pathString,
                    compilerExecutable = compiler.absolutePath,
                    failOnCompileError = true,
                    failOnValidationError = true,
                    cleanNativeAnnotations = true
                )
            )
        )
        instance.execute()

        val transformed = instance.workRes.inputClassMap.getValue("test/NativePipelineFullJvm")
        assertTrue(transformed.methods.single { it.name == "safeDivide" }.access and Opcodes.ACC_NATIVE != 0)
        assertTrue(transformed.methods.single { it.name == "monitorAdd" }.access and Opcodes.ACC_NATIVE != 0)
        assertTrue(transformed.methods.single { it.name == "fieldAdd" }.access and Opcodes.ACC_NATIVE != 0)

        val javaHome = Path.of(System.getProperty("java.home"))
        val javaExe = javaHome.resolve("bin").resolve(if (File.separatorChar == '\\') "java.exe" else "java")
        val process = ProcessBuilder(
            javaExe.absolutePathString(),
            "-cp",
            outputJar.absolutePathString(),
            "test.NativePipelineFullJvm"
        ).redirectErrorStream(true)
            .apply {
                val oldPath = environment()["PATH"].orEmpty()
                environment()["PATH"] = compiler.parentFile.absolutePath + File.pathSeparator + oldPath
            }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        assertEquals(0, exitCode, "Dumped full JVM native jar failed with exit code $exitCode:\n$output")
        assertTrue("FULL_JVM_OK" in output)
    }

    @Test
    fun executeDumpsRunnableJarWithInterfaceNativeProxiesWhenToolchainExists() {
        val compiler = findHostCompiler()
        if (compiler == null) {
            println("Skipping interface native jar E2E: no C++ compiler found on PATH")
            return
        }
        val includeRoot = resolveJniIncludeRoot(Path.of(System.getProperty("java.home")))
        val includeOs = includeRoot.resolve(NativePlatform.current().jniIncludeOs)
        if (!includeRoot.resolve("jni.h").exists() || !includeOs.exists()) {
            println("Skipping interface native jar E2E: JNI headers not found under $includeRoot and $includeOs")
            return
        }

        val inputDir = createTempDirectory("grunteon-native-interface-input")
        val outputJar = createTempFile("grunteon-native-interface-output", ".jar")
        writeClass(inputDir, nativeInterfaceClass())
        writeClass(inputDir, nativeInterfaceImplClass())

        val instance = Grunteon.create(
            ObfConfig(
                globalConfig = GlobalConfig(
                    input = inputDir.pathString,
                    output = outputJar.pathString,
                    dumpMappings = false,
                    exclusions = emptyList(),
                    mixinExclusions = emptyList(),
                    missingCheck = false
                ),
                nativePipeline = NativePipelineConfig(
                    enabled = true,
                    workDir = createTempDirectory("grunteon-native-interface-work").pathString,
                    compilerExecutable = compiler.absolutePath,
                    failOnCompileError = true,
                    failOnValidationError = true,
                    cleanNativeAnnotations = true
                )
            )
        )
        instance.execute()

        val transformedInterface = instance.workRes.inputClassMap.getValue("test/NativePipelineFace")
        assertTrue(transformedInterface.methods.single { it.name == "defaultAdd" }.access and Opcodes.ACC_NATIVE == 0)
        assertTrue(transformedInterface.methods.single { it.name == "staticAdd" }.access and Opcodes.ACC_NATIVE == 0)
        assertTrue(instance.workRes.inputClassMap.values.any { classNode ->
            classNode.name.startsWith("net/spartanb312/grunteon/runtime/NativeLoader") &&
                classNode.methods.any { it.name.startsWith("grt_native_interface_") }
        })

        val javaHome = Path.of(System.getProperty("java.home"))
        val javaExe = javaHome.resolve("bin").resolve(if (File.separatorChar == '\\') "java.exe" else "java")
        val process = ProcessBuilder(
            javaExe.absolutePathString(),
            "-cp",
            outputJar.absolutePathString(),
            "test.NativePipelineFaceImpl"
        ).redirectErrorStream(true)
            .apply {
                val oldPath = environment()["PATH"].orEmpty()
                environment()["PATH"] = compiler.parentFile.absolutePath + File.pathSeparator + oldPath
            }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        assertEquals(0, exitCode, "Dumped interface native jar failed with exit code $exitCode:\n$output")
        assertTrue("INTERFACE_OK" in output)
    }

    @Test
    fun executeDumpsRunnableJarWithClassInitializerNativeProxyWhenToolchainExists() {
        val compiler = findHostCompiler()
        if (compiler == null) {
            println("Skipping class initializer native jar E2E: no C++ compiler found on PATH")
            return
        }
        val includeRoot = resolveJniIncludeRoot(Path.of(System.getProperty("java.home")))
        val includeOs = includeRoot.resolve(NativePlatform.current().jniIncludeOs)
        if (!includeRoot.resolve("jni.h").exists() || !includeOs.exists()) {
            println("Skipping class initializer native jar E2E: JNI headers not found under $includeRoot and $includeOs")
            return
        }

        val inputDir = createTempDirectory("grunteon-native-clinit-input")
        val outputJar = createTempFile("grunteon-native-clinit-output", ".jar")
        writeClass(inputDir, nativeClassInitializerClass())

        val instance = Grunteon.create(
            ObfConfig(
                globalConfig = GlobalConfig(
                    input = inputDir.pathString,
                    output = outputJar.pathString,
                    dumpMappings = false,
                    exclusions = emptyList(),
                    mixinExclusions = emptyList(),
                    missingCheck = false
                ),
                nativePipeline = NativePipelineConfig(
                    enabled = true,
                    workDir = createTempDirectory("grunteon-native-clinit-work").pathString,
                    compilerExecutable = compiler.absolutePath,
                    failOnCompileError = true,
                    failOnValidationError = true,
                    cleanNativeAnnotations = true
                )
            )
        )
        instance.execute()

        val transformed = instance.workRes.inputClassMap.getValue("test/NativePipelineClinit")
        assertTrue(transformed.methods.any {
            it.name.startsWith("grt_native_clinit_") &&
                it.access and Opcodes.ACC_NATIVE != 0
        })
        val clinit = transformed.methods.single { it.name == "<clinit>" }
        val clinitCalls = clinit.instructions.toArray().filterIsInstance<MethodInsnNode>()
        assertTrue(clinitCalls.any { it.name == "registerNativesForClass" })
        assertTrue(clinitCalls.any { it.name.startsWith("grt_native_clinit_") })

        val javaHome = Path.of(System.getProperty("java.home"))
        val javaExe = javaHome.resolve("bin").resolve(if (File.separatorChar == '\\') "java.exe" else "java")
        val process = ProcessBuilder(
            javaExe.absolutePathString(),
            "-cp",
            outputJar.absolutePathString(),
            "test.NativePipelineClinit"
        ).redirectErrorStream(true)
            .apply {
                val oldPath = environment()["PATH"].orEmpty()
                environment()["PATH"] = compiler.parentFile.absolutePath + File.pathSeparator + oldPath
            }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        assertEquals(0, exitCode, "Dumped class initializer native jar failed with exit code $exitCode:\n$output")
        assertTrue("CLINIT_OK" in output)
    }

    @Test
    fun executeDumpsRunnableJarWithObjectArrayAndConstantFullJvmFeaturesWhenToolchainExists() {
        val compiler = findHostCompiler()
        if (compiler == null) {
            println("Skipping object/array/constant native jar E2E: no C++ compiler found on PATH")
            return
        }
        val includeRoot = resolveJniIncludeRoot(Path.of(System.getProperty("java.home")))
        val includeOs = includeRoot.resolve(NativePlatform.current().jniIncludeOs)
        if (!includeRoot.resolve("jni.h").exists() || !includeOs.exists()) {
            println("Skipping object/array/constant native jar E2E: JNI headers not found under $includeRoot and $includeOs")
            return
        }

        val inputDir = createTempDirectory("grunteon-native-objects-input")
        val outputJar = createTempFile("grunteon-native-objects-output", ".jar")
        writeClass(inputDir, objectArrayAndConstantsClass())

        val instance = Grunteon.create(
            ObfConfig(
                globalConfig = GlobalConfig(
                    input = inputDir.pathString,
                    output = outputJar.pathString,
                    dumpMappings = false,
                    exclusions = emptyList(),
                    mixinExclusions = emptyList(),
                    missingCheck = false
                ),
                nativePipeline = NativePipelineConfig(
                    enabled = true,
                    workDir = createTempDirectory("grunteon-native-objects-work").pathString,
                    compilerExecutable = compiler.absolutePath,
                    failOnCompileError = true,
                    failOnValidationError = true,
                    cleanNativeAnnotations = true
                )
            )
        )
        instance.execute()

        val transformed = instance.workRes.inputClassMap.getValue("test/NativePipelineObjects")
        listOf(
            "objectPipeline",
            "primitiveArrayPipeline",
            "referenceArrayPipeline",
            "multiArrayPipeline",
            "constantPipeline"
        ).forEach { methodName ->
            assertTrue(
                transformed.methods.single { it.name == methodName }.access and Opcodes.ACC_NATIVE != 0,
                "$methodName was not committed as native"
            )
        }

        val javaHome = Path.of(System.getProperty("java.home"))
        val javaExe = javaHome.resolve("bin").resolve(if (File.separatorChar == '\\') "java.exe" else "java")
        val process = ProcessBuilder(
            javaExe.absolutePathString(),
            "-cp",
            outputJar.absolutePathString(),
            "test.NativePipelineObjects"
        ).redirectErrorStream(true)
            .apply {
                val oldPath = environment()["PATH"].orEmpty()
                environment()["PATH"] = compiler.parentFile.absolutePath + File.pathSeparator + oldPath
            }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        assertEquals(0, exitCode, "Dumped object/array/constant native jar failed with exit code $exitCode:\n$output")
        assertTrue("OBJECT_ARRAY_CONST_OK" in output)
    }

    private fun fakeCompiler(root: Path, succeeds: Boolean): Path {
        val script = root.resolve(if (File.separatorChar == '\\') "fake-compiler.bat" else "fake-compiler.sh")
        if (File.separatorChar == '\\') {
            script.writeText(
                """
                @echo off
                set "out="
                :loop
                if "%~1"=="" goto done
                if not "%~1"=="-o" goto next
                shift
                set "out=%~1"
                :next
                shift
                goto loop
                :done
                if "%out%"=="" exit /b 2
                ${if (succeeds) "echo native-test>\"%out%\"" else "exit /b 7"}
                exit /b 0
                """.trimIndent()
            )
        } else {
            script.writeText(
                """
                #!/bin/sh
                out=""
                while [ "${'$'}#" -gt 0 ]; do
                    if [ "${'$'}1" = "-o" ]; then
                        shift
                        out="${'$'}1"
                    fi
                    shift || break
                done
                [ -z "${'$'}out" ] && exit 2
                ${if (succeeds) "printf 'native-test' > \"${'$'}out\"" else "exit 7"}
                exit 0
                """.trimIndent()
            )
            script.toFile().setExecutable(true)
        }
        return script
    }

    private fun findHostCompiler(): File? {
        return sequenceOf("clang++", "g++", "clang-cl", "cl")
            .mapNotNull(::findExecutable)
            .firstOrNull()
    }

    private fun findExecutable(name: String): File? {
        val path = System.getenv("PATH") ?: return null
        val extensions = if (File.separatorChar == '\\') {
            val pathext = System.getenv("PATHEXT")
                ?.split(File.pathSeparatorChar)
                ?.filter { it.isNotBlank() }
                ?: listOf(".COM", ".EXE", ".BAT", ".CMD")
            listOf("") + pathext
        } else {
            listOf("")
        }
        return path
            .split(File.pathSeparatorChar)
            .asSequence()
            .flatMap { dir -> extensions.asSequence().map { ext -> File(dir, name + ext) } }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    private fun resolveJniIncludeRoot(javaHome: Path): Path {
        val direct = javaHome.resolve("include")
        if (direct.exists()) return direct
        return javaHome.parent?.resolve("include") ?: direct
    }

    private fun instanceWith(vararg classes: ClassNode): Grunteon {
        val root = createTempDirectory("grunteon-native-runner")
        classes.forEach { writeClass(root, it) }
        return Grunteon.create(
            ObfConfig(
                globalConfig = GlobalConfig(
                    input = root.pathString,
                    output = null,
                    dumpMappings = false,
                    exclusions = emptyList(),
                    mixinExclusions = emptyList()
                )
            )
        )
    }

    private fun writeClass(root: Path, classNode: ClassNode) {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        classNode.accept(writer)
        val file = root.resolve("${classNode.name}.class")
        file.parent.createDirectories()
        file.writeBytes(writer.toByteArray())
    }

    private fun classNode(name: String, vararg methods: MethodNode): ClassNode {
        return ClassNode().apply {
            version = Opcodes.V1_8
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER
            this.name = name
            superName = "java/lang/Object"
            this.methods.addAll(methods)
        }
    }

    private fun fullJvmFeatureClass(): ClassNode {
        return classNode(
            "test/NativePipelineFullJvm",
            fullJvmMainMethod(),
            constructorWithValue(),
            safeDivideMethod().appendAnnotation(NATIVE_INCLUDED),
            monitorAddMethod().appendAnnotation(NATIVE_INCLUDED),
            fieldAddMethod().appendAnnotation(NATIVE_INCLUDED)
        ).apply {
            fields.add(FieldNode(Opcodes.ACC_PRIVATE, "value", "I", null, null))
        }
    }

    private fun nativeInterfaceClass(): ClassNode {
        return ClassNode().apply {
            version = Opcodes.V1_8
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT or Opcodes.ACC_INTERFACE
            name = "test/NativePipelineFace"
            superName = "java/lang/Object"
            fields.add(FieldNode(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL,
                "BASE",
                "I",
                null,
                null
            ))
            methods.add(interfaceClinitMethod().appendAnnotation(NATIVE_INCLUDED))
            methods.add(interfaceDefaultAddMethod().appendAnnotation(NATIVE_INCLUDED))
            methods.add(interfaceStaticAddMethod().appendAnnotation(NATIVE_INCLUDED))
        }
    }

    private fun nativeInterfaceImplClass(): ClassNode {
        return classNode(
            "test/NativePipelineFaceImpl",
            interfaceImplConstructor(),
            interfaceMainMethod()
        ).apply {
            interfaces.add("test/NativePipelineFace")
        }
    }

    private fun nativeClassInitializerClass(): ClassNode {
        return classNode(
            "test/NativePipelineClinit",
            classInitializerMainMethod(),
            classNativeClinitMethod().appendAnnotation(NATIVE_INCLUDED)
        ).apply {
            fields.add(FieldNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "VALUE", "I", null, null))
        }
    }

    private fun objectArrayAndConstantsClass(): ClassNode {
        return classNode(
            "test/NativePipelineObjects",
            objectArrayConstantsMainMethod(),
            objectPipelineMethod().appendAnnotation(NATIVE_INCLUDED),
            primitiveArrayPipelineMethod().appendAnnotation(NATIVE_INCLUDED),
            referenceArrayPipelineMethod().appendAnnotation(NATIVE_INCLUDED),
            multiArrayPipelineMethod().appendAnnotation(NATIVE_INCLUDED),
            constantPipelineMethod().appendAnnotation(NATIVE_INCLUDED)
        )
    }

    private fun mainMethod(): MethodNode {
        val success = LabelNode()
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "main",
            "([Ljava/lang/String;)V",
            null,
            null
        ).apply {
            instructions.add(LdcInsnNode(20))
            instructions.add(LdcInsnNode(22))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "test/NativePipelineJar",
                "nativeAdd",
                "(II)I",
                false
            ))
            instructions.add(LdcInsnNode(42))
            instructions.add(JumpInsnNode(Opcodes.IF_ICMPEQ, success))
            instructions.add(TypeInsnNode(Opcodes.NEW, "java/lang/AssertionError"))
            instructions.add(InsnNode(Opcodes.DUP))
            instructions.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/AssertionError", "<init>", "()V", false))
            instructions.add(InsnNode(Opcodes.ATHROW))
            instructions.add(success)
            instructions.add(FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"))
            instructions.add(LdcInsnNode("OK"))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/io/PrintStream",
                "print",
                "(Ljava/lang/String;)V",
                false
            ))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 3
            maxLocals = 1
        }
    }

    private fun classInitializerMainMethod(): MethodNode {
        val success = LabelNode()
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "main",
            "([Ljava/lang/String;)V",
            null,
            null
        ).apply {
            instructions.add(FieldInsnNode(Opcodes.GETSTATIC, "test/NativePipelineClinit", "VALUE", "I"))
            instructions.add(LdcInsnNode(42))
            instructions.add(JumpInsnNode(Opcodes.IF_ICMPEQ, success))
            instructions.add(TypeInsnNode(Opcodes.NEW, "java/lang/AssertionError"))
            instructions.add(InsnNode(Opcodes.DUP))
            instructions.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/AssertionError", "<init>", "()V", false))
            instructions.add(InsnNode(Opcodes.ATHROW))
            instructions.add(success)
            instructions.add(FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"))
            instructions.add(LdcInsnNode("CLINIT_OK"))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/io/PrintStream",
                "print",
                "(Ljava/lang/String;)V",
                false
            ))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 2
            maxLocals = 1
        }
    }

    private fun classNativeClinitMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null).apply {
            instructions.add(LdcInsnNode(42))
            instructions.add(FieldInsnNode(Opcodes.PUTSTATIC, "test/NativePipelineClinit", "VALUE", "I"))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 1
            maxLocals = 0
        }
    }

    private fun objectArrayConstantsMainMethod(): MethodNode {
        val failure = LabelNode()
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "main",
            "([Ljava/lang/String;)V",
            null,
            null
        ).apply {
            instructions.add(LdcInsnNode(7))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "test/NativePipelineObjects",
                "objectPipeline",
                "(I)I",
                false
            ))
            instructions.add(InsnNode(Opcodes.ICONST_4))
            instructions.add(JumpInsnNode(Opcodes.IF_ICMPNE, failure))

            instructions.add(LdcInsnNode(5))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "test/NativePipelineObjects",
                "primitiveArrayPipeline",
                "(I)I",
                false
            ))
            instructions.add(LdcInsnNode(18))
            instructions.add(JumpInsnNode(Opcodes.IF_ICMPNE, failure))

            instructions.add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "test/NativePipelineObjects",
                "referenceArrayPipeline",
                "()I",
                false
            ))
            instructions.add(InsnNode(Opcodes.ICONST_4))
            instructions.add(JumpInsnNode(Opcodes.IF_ICMPNE, failure))

            instructions.add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "test/NativePipelineObjects",
                "multiArrayPipeline",
                "()I",
                false
            ))
            instructions.add(InsnNode(Opcodes.ICONST_5))
            instructions.add(JumpInsnNode(Opcodes.IF_ICMPNE, failure))

            instructions.add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "test/NativePipelineObjects",
                "constantPipeline",
                "()I",
                false
            ))
            instructions.add(LdcInsnNode(7))
            instructions.add(JumpInsnNode(Opcodes.IF_ICMPNE, failure))

            instructions.add(FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"))
            instructions.add(LdcInsnNode("OBJECT_ARRAY_CONST_OK"))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/io/PrintStream",
                "print",
                "(Ljava/lang/String;)V",
                false
            ))
            instructions.add(InsnNode(Opcodes.RETURN))

            instructions.add(failure)
            instructions.add(TypeInsnNode(Opcodes.NEW, "java/lang/AssertionError"))
            instructions.add(InsnNode(Opcodes.DUP))
            instructions.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/AssertionError", "<init>", "()V", false))
            instructions.add(InsnNode(Opcodes.ATHROW))
            maxStack = 3
            maxLocals = 1
        }
    }

    private fun objectPipelineMethod(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "objectPipeline",
            "(I)I",
            null,
            null
        ).apply {
            instructions.add(TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"))
            instructions.add(InsnNode(Opcodes.DUP))
            instructions.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false))
            instructions.add(LdcInsnNode("abc"))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/StringBuilder",
                "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false
            ))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/StringBuilder",
                "append",
                "(I)Ljava/lang/StringBuilder;",
                false
            ))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/StringBuilder",
                "toString",
                "()Ljava/lang/String;",
                false
            ))
            instructions.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 3
            maxLocals = 1
        }
    }

    private fun primitiveArrayPipelineMethod(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "primitiveArrayPipeline",
            "(I)I",
            null,
            null
        ).apply {
            instructions.add(InsnNode(Opcodes.ICONST_3))
            instructions.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT))
            instructions.add(VarInsnNode(Opcodes.ASTORE, 1))

            instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
            instructions.add(InsnNode(Opcodes.ICONST_0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(InsnNode(Opcodes.IASTORE))

            instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IASTORE))

            instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
            instructions.add(InsnNode(Opcodes.ICONST_2))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(InsnNode(Opcodes.ICONST_2))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IASTORE))

            instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
            instructions.add(InsnNode(Opcodes.ICONST_0))
            instructions.add(InsnNode(Opcodes.IALOAD))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(InsnNode(Opcodes.IALOAD))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
            instructions.add(InsnNode(Opcodes.ICONST_2))
            instructions.add(InsnNode(Opcodes.IALOAD))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 4
            maxLocals = 2
        }
    }

    private fun referenceArrayPipelineMethod(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "referenceArrayPipeline",
            "()I",
            null,
            null
        ).apply {
            instructions.add(InsnNode(Opcodes.ICONST_2))
            instructions.add(TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"))
            instructions.add(VarInsnNode(Opcodes.ASTORE, 0))

            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.ICONST_0))
            instructions.add(LdcInsnNode("a"))
            instructions.add(InsnNode(Opcodes.AASTORE))

            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(LdcInsnNode("bc"))
            instructions.add(InsnNode(Opcodes.AASTORE))

            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.ICONST_0))
            instructions.add(InsnNode(Opcodes.AALOAD))
            instructions.add(TypeInsnNode(Opcodes.CHECKCAST, "java/lang/String"))
            instructions.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false))

            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(InsnNode(Opcodes.AALOAD))
            instructions.add(TypeInsnNode(Opcodes.CHECKCAST, "java/lang/String"))
            instructions.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false))
            instructions.add(InsnNode(Opcodes.IADD))

            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(TypeInsnNode(Opcodes.INSTANCEOF, "[Ljava/lang/String;"))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 3
            maxLocals = 1
        }
    }

    private fun multiArrayPipelineMethod(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "multiArrayPipeline",
            "()I",
            null,
            null
        ).apply {
            instructions.add(InsnNode(Opcodes.ICONST_2))
            instructions.add(InsnNode(Opcodes.ICONST_3))
            instructions.add(MultiANewArrayInsnNode("[[I", 2))
            instructions.add(VarInsnNode(Opcodes.ASTORE, 0))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.ARRAYLENGTH))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.ICONST_0))
            instructions.add(InsnNode(Opcodes.AALOAD))
            instructions.add(InsnNode(Opcodes.ARRAYLENGTH))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 3
            maxLocals = 1
        }
    }

    private fun constantPipelineMethod(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "constantPipeline",
            "()I",
            null,
            null
        ).apply {
            instructions.add(LdcInsnNode(Type.getObjectType("java/lang/String")))
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(LdcInsnNode(Type.getMethodType("(I)Ljava/lang/String;")))
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(LdcInsnNode(Handle(
                Opcodes.H_INVOKESTATIC,
                "java/lang/Integer",
                "bitCount",
                "(I)I",
                false
            )))
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(LdcInsnNode(7))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 0
        }
    }

    private fun interfaceMainMethod(): MethodNode {
        val failure = LabelNode()
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "main",
            "([Ljava/lang/String;)V",
            null,
            null
        ).apply {
            instructions.add(LdcInsnNode(31))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "test/NativePipelineFace",
                "staticAdd",
                "(I)I",
                true
            ))
            instructions.add(LdcInsnNode(42))
            instructions.add(JumpInsnNode(Opcodes.IF_ICMPNE, failure))

            instructions.add(TypeInsnNode(Opcodes.NEW, "test/NativePipelineFaceImpl"))
            instructions.add(InsnNode(Opcodes.DUP))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "test/NativePipelineFaceImpl",
                "<init>",
                "()V",
                false
            ))
            instructions.add(LdcInsnNode(31))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                "test/NativePipelineFace",
                "defaultAdd",
                "(I)I",
                true
            ))
            instructions.add(LdcInsnNode(42))
            instructions.add(JumpInsnNode(Opcodes.IF_ICMPNE, failure))

            instructions.add(FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"))
            instructions.add(LdcInsnNode("INTERFACE_OK"))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/io/PrintStream",
                "print",
                "(Ljava/lang/String;)V",
                false
            ))
            instructions.add(InsnNode(Opcodes.RETURN))

            instructions.add(failure)
            instructions.add(TypeInsnNode(Opcodes.NEW, "java/lang/AssertionError"))
            instructions.add(InsnNode(Opcodes.DUP))
            instructions.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/AssertionError", "<init>", "()V", false))
            instructions.add(InsnNode(Opcodes.ATHROW))
            maxStack = 3
            maxLocals = 1
        }
    }

    private fun interfaceImplConstructor(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun interfaceClinitMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null).apply {
            instructions.add(LdcInsnNode(11))
            instructions.add(FieldInsnNode(Opcodes.PUTSTATIC, "test/NativePipelineFace", "BASE", "I"))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 1
            maxLocals = 0
        }
    }

    private fun interfaceDefaultAddMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC, "defaultAdd", "(I)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(FieldInsnNode(Opcodes.GETSTATIC, "test/NativePipelineFace", "BASE", "I"))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun interfaceStaticAddMethod(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "staticAdd",
            "(I)I",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(FieldInsnNode(Opcodes.GETSTATIC, "test/NativePipelineFace", "BASE", "I"))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 1
        }
    }

    private fun fullJvmMainMethod(): MethodNode {
        val failure = LabelNode()
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "main",
            "([Ljava/lang/String;)V",
            null,
            null
        ).apply {
            instructions.add(LdcInsnNode(8))
            instructions.add(LdcInsnNode(2))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "test/NativePipelineFullJvm",
                "safeDivide",
                "(II)I",
                false
            ))
            instructions.add(LdcInsnNode(4))
            instructions.add(JumpInsnNode(Opcodes.IF_ICMPNE, failure))

            instructions.add(LdcInsnNode(8))
            instructions.add(LdcInsnNode(0))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "test/NativePipelineFullJvm",
                "safeDivide",
                "(II)I",
                false
            ))
            instructions.add(InsnNode(Opcodes.ICONST_M1))
            instructions.add(JumpInsnNode(Opcodes.IF_ICMPNE, failure))

            instructions.add(TypeInsnNode(Opcodes.NEW, "java/lang/Object"))
            instructions.add(InsnNode(Opcodes.DUP))
            instructions.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false))
            instructions.add(LdcInsnNode(20))
            instructions.add(LdcInsnNode(22))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "test/NativePipelineFullJvm",
                "monitorAdd",
                "(Ljava/lang/Object;II)I",
                false
            ))
            instructions.add(LdcInsnNode(42))
            instructions.add(JumpInsnNode(Opcodes.IF_ICMPNE, failure))

            instructions.add(TypeInsnNode(Opcodes.NEW, "test/NativePipelineFullJvm"))
            instructions.add(InsnNode(Opcodes.DUP))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "test/NativePipelineFullJvm",
                "<init>",
                "()V",
                false
            ))
            instructions.add(LdcInsnNode(12))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "test/NativePipelineFullJvm",
                "fieldAdd",
                "(I)I",
                false
            ))
            instructions.add(LdcInsnNode(42))
            instructions.add(JumpInsnNode(Opcodes.IF_ICMPNE, failure))

            instructions.add(FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"))
            instructions.add(LdcInsnNode("FULL_JVM_OK"))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/io/PrintStream",
                "print",
                "(Ljava/lang/String;)V",
                false
            ))
            instructions.add(InsnNode(Opcodes.RETURN))

            instructions.add(failure)
            instructions.add(TypeInsnNode(Opcodes.NEW, "java/lang/AssertionError"))
            instructions.add(InsnNode(Opcodes.DUP))
            instructions.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/AssertionError", "<init>", "()V", false))
            instructions.add(InsnNode(Opcodes.ATHROW))
            maxStack = 4
            maxLocals = 1
        }
    }

    private fun constructorWithValue(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(LdcInsnNode(30))
            instructions.add(FieldInsnNode(Opcodes.PUTFIELD, "test/NativePipelineFullJvm", "value", "I"))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 2
            maxLocals = 1
        }
    }

    private fun safeDivideMethod(): MethodNode {
        val start = LabelNode()
        val end = LabelNode()
        val handler = LabelNode()
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "safeDivide",
            "(II)I",
            null,
            null
        ).apply {
            instructions.add(start)
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IDIV))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(end)
            instructions.add(handler)
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.ICONST_M1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            tryCatchBlocks.add(TryCatchBlockNode(start, end, handler, "java/lang/ArithmeticException"))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun monitorAddMethod(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "monitorAdd",
            "(Ljava/lang/Object;II)I",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.MONITORENTER))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 2))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(VarInsnNode(Opcodes.ISTORE, 3))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.MONITOREXIT))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 3))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 4
        }
    }

    private fun fieldAddMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC, "fieldAdd", "(I)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(FieldInsnNode(Opcodes.GETFIELD, "test/NativePipelineFullJvm", "value", "I"))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun ClassNode.toBytes(): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        accept(writer)
        return writer.toByteArray()
    }

    private fun intHelper(name: String): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            name,
            "(II)I",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private class NativeE2EClassLoader(
        parent: ClassLoader,
        private val classes: Map<String, ByteArray>,
        private val resources: Map<String, ByteArray>
    ) : ClassLoader(parent) {

        override fun findClass(name: String): Class<*> {
            val bytes = classes[name] ?: return super.findClass(name)
            return defineClass(name, bytes, 0, bytes.size)
        }

        override fun getResourceAsStream(name: String): InputStream? {
            resources[name]?.let { return ByteArrayInputStream(it) }
            return super.getResourceAsStream(name)
        }
    }
}
