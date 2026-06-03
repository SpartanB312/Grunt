package net.spartanb312.grunt.ir.export

import net.spartanb312.grunt.ir.core.IrFunction
import net.spartanb312.grunt.ir.core.IrVerifier
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

data class IrTextExportOptions(
    val charset: Charset = StandardCharsets.UTF_8,
    val createDirectories: Boolean = true,
    val overwrite: Boolean = true,
    val verifyBeforeExport: Boolean = false,
    val trailingNewLine: Boolean = true
)

class IrTextExporter(
    private val printer: IrStrictPrinter = IrStrictPrinter(),
    private val options: IrTextExportOptions = IrTextExportOptions()
) {
    fun export(function: IrFunction, target: Path): Path {
        if (options.verifyBeforeExport) {
            IrVerifier.verify(function).requireValid()
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

    fun export(functions: Iterable<IrFunction>, targetDirectory: Path): List<Path> {
        if (options.createDirectories) {
            Files.createDirectories(targetDirectory)
        }

        return functions.map { export(it, targetDirectory) }
    }

    private fun resolveOutputPath(function: IrFunction, target: Path): Path {
        val output = if (Files.exists(target) && Files.isDirectory(target)) {
            target.resolve(defaultFileName(function))
        } else if (target.fileName == null) {
            target.resolve(defaultFileName(function))
        } else {
            target
        }
        return ensureIrExtension(output)
    }

    private fun defaultFileName(function: IrFunction): String {
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

fun IrFunction.exportIr(target: Path, options: IrTextExportOptions = IrTextExportOptions()): Path {
    return IrTextExporter(options = options).export(this, target)
}
