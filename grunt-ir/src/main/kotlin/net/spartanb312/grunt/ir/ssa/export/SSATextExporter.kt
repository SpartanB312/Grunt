package net.spartanb312.grunt.ir.ssa.export

import net.spartanb312.grunt.ir.ssa.core.SSAFunction
import net.spartanb312.grunt.ir.ssa.core.SSAVerifier
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

data class SSATextExportOptions(
    val charset: Charset = StandardCharsets.UTF_8,
    val createDirectories: Boolean = true,
    val overwrite: Boolean = true,
    val verifyBeforeExport: Boolean = false,
    val trailingNewLine: Boolean = true
)

class SSATextExporter(
    private val printer: SSAStrictPrinter = SSAStrictPrinter(),
    private val options: SSATextExportOptions = SSATextExportOptions()
) {
    fun export(function: SSAFunction, target: Path): Path {
        if (options.verifyBeforeExport) {
            SSAVerifier.verify(function).requireValid()
        }

        val output = resolveOutputPath(function, target)
        if (options.createDirectories) {
            output.parent?.let { Files.createDirectories(it) }
        }

        val text = buildString {
            append(printer.print(function))
            if (options.trailingNewLine) append("\n")
        }

        val openOptions = if (options.overwrite) {
            arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
        } else {
            arrayOf(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        }

        Files.write(output, text.toByteArray(options.charset), *openOptions)
        return output
    }

    fun export(functions: Iterable<SSAFunction>, targetDirectory: Path): List<Path> {
        if (options.createDirectories) {
            Files.createDirectories(targetDirectory)
        }

        return functions.map { export(it, targetDirectory) }
    }

    private fun resolveOutputPath(function: SSAFunction, target: Path): Path {
        val output = if (Files.exists(target) && Files.isDirectory(target)) {
            target.resolve(defaultFileName(function))
        } else if (target.fileName == null) {
            target.resolve(defaultFileName(function))
        } else {
            target
        }
        return ensureIrExtension(output)
    }

    private fun defaultFileName(function: SSAFunction): String {
        val sanitized = function.symbol.name
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifEmpty { "function" }
        return ensureIrExtension(sanitized)
    }

    companion object {
        const val Extension = ".ir"

        fun ensureIrExtension(path: Path): Path {
            val fileName = path.fileName?.toString() ?: return path
            return if (fileName.endsWith(Extension, ignoreCase = true)) {
                path
            } else {
                path.resolveSibling("$fileName$Extension")
            }
        }

        fun ensureIrExtension(fileName: String): String {
            return if (fileName.endsWith(Extension, ignoreCase = true)) fileName else "$fileName$Extension"
        }
    }
}

fun SSAFunction.exportIr(target: Path, options: SSATextExportOptions = SSATextExportOptions()): Path {
    return SSATextExporter(options = options).export(this, target)
}
