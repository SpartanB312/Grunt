package net.spartanb312.grunt.ir.flow.visual

import net.spartanb312.grunt.ir.flow.core.FlowBlock
import net.spartanb312.grunt.ir.flow.core.FlowBlockKind
import net.spartanb312.grunt.ir.flow.core.FlowEdge
import net.spartanb312.grunt.ir.flow.core.FlowEdgeSemantics
import net.spartanb312.grunt.ir.flow.core.FlowExceptionRegion
import net.spartanb312.grunt.ir.flow.core.FlowFrame
import net.spartanb312.grunt.ir.flow.core.FlowFrameValue
import net.spartanb312.grunt.ir.flow.core.FlowGotoJump
import net.spartanb312.grunt.ir.flow.core.FlowIfJump
import net.spartanb312.grunt.ir.flow.core.FlowJump
import net.spartanb312.grunt.ir.flow.core.FlowJumpInput
import net.spartanb312.grunt.ir.flow.core.FlowMethod
import net.spartanb312.grunt.ir.flow.core.FlowReturnJump
import net.spartanb312.grunt.ir.flow.core.FlowSwitchJump
import net.spartanb312.grunt.ir.flow.core.FlowThrowJump
import net.spartanb312.grunt.ir.flow.core.FlowUnreachableJump
import net.spartanb312.grunt.ir.flow.core.FlowVerifier
import org.objectweb.asm.util.Printer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

data class FlowDotExportOptions(
    val charset: Charset = StandardCharsets.UTF_8,
    val createDirectories: Boolean = true,
    val overwrite: Boolean = true,
    val verifyBeforeExport: Boolean = false,
    val trailingNewLine: Boolean = true,
    val showFrames: Boolean = true,
    val showBodySize: Boolean = true,
    val showJumpInput: Boolean = true,
    val showExceptionEdges: Boolean = true,
    val showLayoutOrder: Boolean = true
)

class FlowDotExporter(
    private val options: FlowDotExportOptions = FlowDotExportOptions()
) {
    fun print(method: FlowMethod): String {
        if (options.verifyBeforeExport) {
            FlowVerifier.verify(method).requireValid()
        }

        val layoutIndex = method.layout.order.withIndex().associate { it.value to it.index }
        val graphName = dotId(defaultGraphName(method))

        return buildString {
            appendLine("digraph $graphName {")
            appendLine("  graph [rankdir=TB, compound=true, bgcolor=\"white\"];")
            appendLine("  node [shape=box, style=\"rounded,filled\", fontname=\"Consolas\", fontsize=10];")
            appendLine("  edge [fontname=\"Consolas\", fontsize=9];")
            appendLine()

            for (block in method.layout.order.ifEmpty { method.blocks }) {
                append("  ")
                append(dotId(block.id.toString()))
                append(" [")
                append("label=")
                append(dotString(nodeLabel(block, layoutIndex[block])))
                append(", fillcolor=")
                append(dotString(blockFill(block.kind)))
                append(", color=")
                append(dotString(blockBorder(block.kind)))
                appendLine("];")
            }

            if (method.edges.isNotEmpty()) appendLine()
            for (edge in method.edges) {
                append("  ")
                append(dotId(edge.from.id.toString()))
                append(" -> ")
                append(dotId(edge.to.id.toString()))
                append(" [")
                append("label=")
                append(dotString(edgeLabel(edge)))
                append(", color=")
                append(dotString(edgeColor(edge.semantics)))
                append(", style=")
                append(dotString(edgeStyle(edge.semantics)))
                appendLine("];")
            }

            if (options.showExceptionEdges && method.exceptionRegions.isNotEmpty()) {
                appendLine()
                method.exceptionRegions.forEachIndexed { index, region ->
                    for (block in region.protectedBlocks) {
                        append("  ")
                        append(dotId(block.id.toString()))
                        append(" -> ")
                        append(dotId(region.handler.id.toString()))
                        append(" [")
                        append("label=")
                        append(dotString(exceptionLabel(index, region)))
                        append(", color=\"#C62828\", style=\"dashed\", constraint=false")
                        appendLine("];")
                    }
                }
            }

            appendLine("}")
        }
    }

    fun export(method: FlowMethod, target: Path): Path {
        val output = resolveOutputPath(method, target)
        if (options.createDirectories) {
            output.parent?.let { Files.createDirectories(it) }
        }

        val text = buildString {
            append(print(method))
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

    private fun nodeLabel(block: FlowBlock, layoutIndex: Int?): String {
        return buildString {
            append(block.id)
            append("\n")
            if (options.showLayoutOrder && layoutIndex != null) {
                append("#")
                append(layoutIndex)
                append(" ")
            }
            append(block.kind)
            if (options.showFrames) {
                append("\nentry ")
                append(frameSummary(block.entryFrame))
                append("\nexit  ")
                append(frameSummary(block.bodyExitFrame))
            }
            if (options.showBodySize) {
                append("\nbody=")
                append(block.body.size)
            }
            append("\n")
            append(jumpSummary(block.jump))
        }
    }

    private fun edgeLabel(edge: FlowEdge): String {
        return "${edge.port.displayName} / ${edge.semantics}"
    }

    private fun exceptionLabel(index: Int, region: FlowExceptionRegion): String {
        val type = region.catchType?.internalName ?: "any"
        return "catch#$index $type"
    }

    private fun jumpSummary(jump: FlowJump): String {
        val main = when (jump) {
            is FlowGotoJump -> "GOTO ${jump.mode}"
            is FlowIfJump -> "IF ${opcodeName(jump.opcode)}"
            is FlowSwitchJump -> "SWITCH cases=${jump.keyPorts.size}"
            is FlowReturnJump -> "RETURN"
            is FlowThrowJump -> "THROW"
            FlowUnreachableJump -> "UNREACHABLE"
        }
        if (!options.showJumpInput) return main
        return "$main input=${inputSummary(jump.input)}"
    }

    private fun inputSummary(input: FlowJumpInput): String {
        return when (input) {
            FlowJumpInput.None -> "none"
            is FlowJumpInput.StackConsumed -> "stack${values(input.produced)}"
            is FlowJumpInput.Generated -> "generated${values(input.produced)} ${input.guarantee}"
            is FlowJumpInput.CapturedLocal -> {
                val locals = input.locals.joinToString(prefix = "[", postfix = "]") { "l${it.index}" }
                "locals$locals${values(input.produced)}"
            }
        }
    }

    private fun frameSummary(frame: FlowFrame): String {
        return "L${values(frame.locals)} S${values(frame.stack)}"
    }

    private fun values(values: List<FlowFrameValue>): String {
        return values.joinToString(prefix = "[", postfix = "]") { it.displayName }
    }

    private fun blockFill(kind: FlowBlockKind): String {
        return when (kind) {
            FlowBlockKind.Original -> "#FFFFFF"
            FlowBlockKind.Split -> "#F5F5F5"
            FlowBlockKind.Dispatcher -> "#BBDEFB"
            FlowBlockKind.FlattenCase -> "#E3F2FD"
            FlowBlockKind.StateSet -> "#D1C4E9"
            FlowBlockKind.RegionEntry -> "#C8E6C9"
            FlowBlockKind.RegionExit -> "#DCEDC8"
            FlowBlockKind.Bogus -> "#FFE0B2"
            FlowBlockKind.Junk -> "#FFF3E0"
            FlowBlockKind.Trap -> "#FFCDD2"
            FlowBlockKind.Bridge -> "#E0E0E0"
            FlowBlockKind.Synthetic -> "#EEEEEE"
        }
    }

    private fun blockBorder(kind: FlowBlockKind): String {
        return when (kind) {
            FlowBlockKind.Trap -> "#B71C1C"
            FlowBlockKind.Bogus,
            FlowBlockKind.Junk -> "#EF6C00"
            FlowBlockKind.Dispatcher,
            FlowBlockKind.FlattenCase -> "#1565C0"
            FlowBlockKind.RegionEntry,
            FlowBlockKind.RegionExit -> "#2E7D32"
            else -> "#424242"
        }
    }

    private fun edgeColor(semantics: FlowEdgeSemantics): String {
        return when (semantics) {
            FlowEdgeSemantics.Real -> "#212121"
            FlowEdgeSemantics.Bogus -> "#EF6C00"
            FlowEdgeSemantics.OpaqueTrue -> "#2E7D32"
            FlowEdgeSemantics.OpaqueFalse -> "#C62828"
            FlowEdgeSemantics.Junk -> "#F57C00"
            FlowEdgeSemantics.Dispatcher -> "#1565C0"
            FlowEdgeSemantics.Synthetic -> "#757575"
        }
    }

    private fun edgeStyle(semantics: FlowEdgeSemantics): String {
        return when (semantics) {
            FlowEdgeSemantics.Real,
            FlowEdgeSemantics.Dispatcher -> "solid"
            else -> "dashed"
        }
    }

    private fun resolveOutputPath(method: FlowMethod, target: Path): Path {
        val output = if (Files.exists(target) && Files.isDirectory(target)) {
            target.resolve(defaultFileName(method))
        } else if (target.fileName == null) {
            target.resolve(defaultFileName(method))
        } else {
            target
        }
        return ensureDotExtension(output)
    }

    private fun defaultGraphName(method: FlowMethod): String {
        return "${method.ownerInternalName}.${method.name}${method.desc}"
    }

    private fun defaultFileName(method: FlowMethod): String {
        val sanitized = defaultGraphName(method)
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifEmpty { "method" }
        return ensureDotExtension(sanitized)
    }

    private fun opcodeName(opcode: Int): String {
        return Printer.OPCODES.getOrNull(opcode) ?: "opcode_$opcode"
    }

    private fun dotId(value: String): String = dotString(value)

    private fun dotString(value: String): String {
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n") + "\""
    }

    companion object {
        const val Extension = ".dot"

        fun ensureDotExtension(path: Path): Path {
            val fileName = path.fileName?.toString() ?: return path
            return if (fileName.endsWith(Extension, ignoreCase = true)) {
                path
            } else {
                path.resolveSibling("$fileName$Extension")
            }
        }

        fun ensureDotExtension(fileName: String): String {
            return if (fileName.endsWith(Extension, ignoreCase = true)) fileName else "$fileName$Extension"
        }
    }
}

fun FlowMethod.exportDot(target: Path, options: FlowDotExportOptions = FlowDotExportOptions()): Path {
    return FlowDotExporter(options).export(this, target)
}
