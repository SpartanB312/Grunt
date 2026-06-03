package net.spartanb312.grunt.ir

import net.spartanb312.grunt.ir.jvm.JvmIrExportOptions
import net.spartanb312.grunt.ir.jvm.JvmIrExporter
import net.spartanb312.grunt.ir.jvm.JvmIrImporter
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceMethodVisitor
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private const val AimTrainerEntry = "net/spartanb312/boar/AimTrainer.class"

fun main(args: Array<String>) {
    val inputJar = args.getOrNull(0)?.let(Path::of) ?: findDefaultInputJar()
    val outputJar = args.getOrNull(1)?.let(Path::of) ?: Path.of("build", "ir", "roundtrip", "input.ir.jar")
    require(inputJar.toAbsolutePath().normalize() != outputJar.toAbsolutePath().normalize()) {
        "Output jar must be different from input jar: $outputJar"
    }

    val classNode = readClass(inputJar, AimTrainerEntry)
    val originalMethods = classNode.methods.toList()
    val transformedMethods = originalMethods.map { method ->
        if (method.isCodeLess()) {
            println("Skipped code-less method ${method.name}${method.desc}")
            method
        } else {
            transformMethod(classNode.name, method).also {
                println("Transformed ${method.name}${method.desc}")
            }
        }
    }

    classNode.methods.clear()
    classNode.methods.addAll(transformedMethods)
    val transformedClass = writeClass(classNode)
    writeJar(inputJar, outputJar, AimTrainerEntry, transformedClass)

    println("Transformed ${transformedMethods.count { !it.isCodeLess() }} method(s) in ${classNode.name}")
    println("Output jar: $outputJar")
}

private fun findDefaultInputJar(): Path {
    val candidates = listOf(
        Path.of("input.jar"),
        Path.of("..", "input.jar")
    )
    return candidates.firstOrNull { Files.exists(it) }
        ?: error("input.jar was not found in current directory or parent directory")
}

private fun readClass(inputJar: Path, entryName: String): ClassNode {
    ZipFile(inputJar.toFile()).use { zip ->
        val entry = zip.getEntry(entryName)
            ?: error("Class entry $entryName was not found in $inputJar")
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        return ClassNode().also {
            ClassReader(bytes).accept(it, 0)
        }
    }
}

private fun transformMethod(ownerInternalName: String, method: MethodNode): MethodNode {
    val imported = try {
        JvmIrImporter().import(ownerInternalName, method)
    } catch (t: Throwable) {
        throw IllegalStateException("Failed to import $ownerInternalName.${method.name}${method.desc}", t)
    }

    val exported = try {
        JvmIrExporter(
            imported.metadata,
            JvmIrExportOptions(
                access = method.access,
                signature = method.signature,
                exceptions = method.exceptions?.toList() ?: emptyList()
            )
        ).export(imported.function)
    } catch (t: Throwable) {
        throw IllegalStateException("Failed to export $ownerInternalName.${method.name}${method.desc}", t)
    }

    copyMethodMetadata(method, exported)
    return exported
}

private fun MethodNode.isCodeLess(): Boolean {
    return access and (Opcodes.ACC_ABSTRACT or Opcodes.ACC_NATIVE) != 0
}

private fun copyMethodMetadata(from: MethodNode, to: MethodNode) {
    to.parameters = from.parameters
    to.visibleAnnotations = from.visibleAnnotations
    to.invisibleAnnotations = from.invisibleAnnotations
    to.visibleTypeAnnotations = from.visibleTypeAnnotations
    to.invisibleTypeAnnotations = from.invisibleTypeAnnotations
    to.visibleParameterAnnotations = from.visibleParameterAnnotations
    to.invisibleParameterAnnotations = from.invisibleParameterAnnotations
    to.visibleAnnotableParameterCount = from.visibleAnnotableParameterCount
    to.invisibleAnnotableParameterCount = from.invisibleAnnotableParameterCount
    to.annotationDefault = from.annotationDefault
    to.attrs = from.attrs
}

private fun writeClass(classNode: ClassNode): ByteArray {
    return try {
        writeClassUnchecked(classNode)
    } catch (t: Throwable) {
        val failures = classNode.methods.mapNotNull { method ->
            val probe = singleMethodClass(classNode, method)
            try {
                writeClassUnchecked(probe)
                null
            } catch (methodError: Throwable) {
                "${method.name}${method.desc}: ${methodError.message ?: methodError::class.java.name}\n${method.dumpText()}"
            }
        }
        if (failures.isNotEmpty()) {
            throw IllegalStateException("Failed to write transformed class. Failing method(s):\n${failures.joinToString("\n")}", t)
        }
        throw t
    }
}

private fun writeClassUnchecked(classNode: ClassNode): ByteArray {
    val writer = ObjectOnlyClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
    classNode.accept(writer)
    return writer.toByteArray()
}

private fun singleMethodClass(source: ClassNode, method: MethodNode): ClassNode {
    return ClassNode().also {
        it.version = source.version
        it.access = source.access
        it.name = source.name
        it.signature = source.signature
        it.superName = source.superName
        it.interfaces = source.interfaces
        it.methods.add(method)
    }
}

private fun MethodNode.dumpText(): String {
    val textifier = Textifier()
    accept(TraceMethodVisitor(textifier))
    val writer = StringWriter()
    textifier.print(PrintWriter(writer))
    return writer.toString()
}

private fun writeJar(inputJar: Path, outputJar: Path, replacementEntry: String, replacementBytes: ByteArray) {
    outputJar.parent?.let(Files::createDirectories)
    ZipFile(inputJar.toFile()).use { zip ->
        ZipOutputStream(Files.newOutputStream(outputJar)).use { out ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val copy = ZipEntry(entry.name).also {
                    it.comment = entry.comment
                    it.extra = entry.extra
                    if (entry.time >= 0L) it.time = entry.time
                }
                out.putNextEntry(copy)
                if (entry.name == replacementEntry) {
                    out.write(replacementBytes)
                } else if (!entry.isDirectory) {
                    zip.getInputStream(entry).use { it.copyTo(out) }
                }
                out.closeEntry()
            }
        }
    }
}

private class ObjectOnlyClassWriter(flags: Int) : ClassWriter(flags) {
    override fun getCommonSuperClass(type1: String, type2: String): String {
        return "java/lang/Object"
    }
}
