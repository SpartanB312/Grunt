import net.spartanb312.grunteon.testcase.methodrename.OverlapInterface2To1
import org.junit.jupiter.api.Assertions
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.assertEquals

private val TEMP_DIR = Path(System.getProperty("java.io.tmpdir"), "grunteon-test").also {
    it.createDirectories()
}

private fun getCP(clazz: Class<*>) = clazz.protectionDomain.codeSource.location.toURI().toPath()

private val TESTCASE_CP = getCP(OverlapInterface2To1::class.java)
private val JUNIT_API_CP = getCP(Assertions::class.java)
private val JAVA_EXE = ProcessHandle.current().info()
private val JAVA_EXE_COMMAND = JAVA_EXE.command().get()

fun getClassBytecodePath(clazz: Class<*>): Path {
    return TESTCASE_CP
        .resolve(clazz.packageName.split(".").joinToString("/"))
        .resolve("${clazz.simpleName}.class")
}

fun readClassNode(path: Path): ClassNode {
    val reader = ClassReader(path.readBytes())
    val node = ClassNode()
    reader.accept(node, 0)
    return node
}

fun writeClassNode(node: ClassNode): Path {
    val writer = ClassWriter(0)
    node.accept(writer)
    val outputPath = TEMP_DIR.resolve(node.name + ".class")
    outputPath.parent.createDirectories()
    outputPath.outputStream().use { it.write(writer.toByteArray()) }
    return outputPath
}

fun executeTestCase(path: Path) {
    val mian = path.relativeTo(TEMP_DIR).joinToString(".") { it.toString().removeSuffix(".class") }
    val process = ProcessBuilder(
        JAVA_EXE_COMMAND,
        "-Dfile.encoding=UTF-8",
        "-Duser.country=US",
        "-Duser.language=en",
        "-ea",
        "-classpath",
        listOf(TEMP_DIR, JUNIT_API_CP, TESTCASE_CP).joinToString(File.pathSeparator) { it.absolutePathString() },
        mian
    )
    println(process.command())
    val exitCode = process.inheritIO().start().waitFor()
    assertEquals(0, exitCode, "Test case ${path.name} failed with exit code $exitCode")
}