package net.spartanb312.grunt.ir.ssa

import net.spartanb312.grunt.ir.flow.jvm.JvmFlowImporter
import net.spartanb312.grunt.ir.flow.visual.FlowDotExporter
import net.spartanb312.grunt.ir.ssa.export.SSATextExporter
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAImporter
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

private const val AimTrainerEntry = "net/spartanb312/boar/AimTrainer.class"
private const val TargetMethodName = "onLoop"

fun main(args: Array<String>) {
    val inputJar = args.getOrNull(0)?.let(Path::of) ?: findDefaultInputJar()
    val outputFile = args.getOrNull(1)?.let(Path::of)
        ?: Path.of("build", "ir", "roundtrip", "AimTrainer.$TargetMethodName.ir")
    val flowDotFile = args.getOrNull(2)?.let(Path::of)
        ?: Path.of("build", "ir", "flow", "AimTrainer.$TargetMethodName.dot")

    val classNode = readClass(inputJar, AimTrainerEntry)
    val methodNode = classNode.methods.firstOrNull { it.name == TargetMethodName }
        ?: error("Method $TargetMethodName was not found in $AimTrainerEntry")
    val imported = JvmSSAImporter().import(classNode.name, methodNode)
    val output = SSATextExporter().export(imported.function, outputFile)
    val flow = JvmFlowImporter().import(classNode.name, methodNode)
    val flowOutput = FlowDotExporter().export(flow.method, flowDotFile)

    println("Exported ${classNode.name}.${methodNode.name}${methodNode.desc}")
    println("Output IR: $output")
    println("Output Flow DOT: $flowOutput")
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
