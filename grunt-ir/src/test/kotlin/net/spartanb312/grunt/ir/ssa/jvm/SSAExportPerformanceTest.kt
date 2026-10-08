package net.spartanb312.grunt.ir.ssa.jvm

import net.spartanb312.grunt.ir.ssa.core.*
import net.spartanb312.grunt.ir.ssa.transform.SSAControlFlowFlattener
import net.spartanb312.grunt.ir.test.loadTestClass
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import kotlin.test.*

class SSAExportPerformanceTest {
    @Test
    fun scratchParallelCopiesPreserveWideAndReferenceSwapsWithoutGrowingPerEdge() {
        val first = Any()
        val second = Any()
        val values = arrayOf<Any>(11L, 2.5, first, 37L, 8.25, second)
        for (blockCount in listOf(2, 9, 64)) {
            for (returned in 0..2) {
                val function = swapFunction(blockCount, returned)
                SSAVerifier.verify(function).requireValid()
                val exported = JvmSSAExporter().export(function)
                // Ten parameter/local slots plus a ten-slot scratch range; no instruction results.
                assertEquals(20 + 16, exported.maxLocals)
                Analyzer(BasicInterpreter()).analyze(Owner, exported)
                val method = loadTestClass(Owner, exported).getMethod(
                    "swap", Long::class.javaPrimitiveType, Double::class.javaPrimitiveType, Any::class.java,
                    Long::class.javaPrimitiveType, Double::class.javaPrimitiveType, Any::class.java
                )
                val expected = values[returned + if ((blockCount - 1) % 2 == 1) 3 else 0]
                val actual = method.invoke(null, *values)
                if (returned == 2) assertSame(expected, actual) else assertEquals(expected, actual)
            }
        }
    }

    @Test
    fun switchTransfersReuseTheMaximumScratchWidth() {
        for (caseCount in listOf(4, 256)) {
            val ids = SSAIdAllocator()
            val selector = SSAParameter(ids.valueId(), 0, SSAI32Type)
            val fallback = SSAParameter(ids.valueId(), 1, SSAI64Type)
            val entry = SSABlock(ids.blockId())
            val target = SSABlock(ids.blockId())
            val result = SSABlockArg(ids.valueId(), 0, SSAI64Type, SSABlockArgOrigin.FrontendState("local", 1))
            target.args += result
            target.terminator = SSAReturnTerminator(result)
            entry.terminator = SSASwitchTerminator(selector, List(caseCount) { key ->
                SSASwitchCase(key.toLong(), SSASuccessor(target, listOf(SSAIntLiteral(key.toLong(), SSAI64Type))))
            }, SSASuccessor(target, listOf(fallback)))
            val function = SSAFunction(
                SSAFunctionSymbol(ids.symbolId(), Owner + ".select(IJ)J", listOf(SSAI32Type, SSAI64Type), SSAI64Type),
                mutableListOf(selector, fallback), mutableListOf(entry, target), entry
            )
            SSAVerifier.verify(function).requireValid()
            val exported = JvmSSAExporter().export(function)
            assertEquals(3 + 2 + 16, exported.maxLocals)
            Analyzer(BasicInterpreter()).analyze(Owner, exported)
            val method = loadTestClass(Owner, exported).getMethod("select", Int::class.javaPrimitiveType, Long::class.javaPrimitiveType)
            assertEquals(2L, method.invoke(null, 2, 999L))
            assertEquals(999L, method.invoke(null, -1, 999L))
        }
    }

    @Test
    fun constructorReferenceCanCrossReusedScratchTransfers() {
        val init = LabelNode()
        val exit = LabelNode()
        val original = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "create", "()Ljava/lang/StringBuilder;", null, null).apply {
            instructions.add(TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"))
            instructions.add(VarInsnNode(Opcodes.ASTORE, 0))
            instructions.add(JumpInsnNode(Opcodes.GOTO, init))
            instructions.add(init)
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false))
            instructions.add(JumpInsnNode(Opcodes.GOTO, exit))
            instructions.add(exit)
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxLocals = 1
            maxStack = 1
        }
        Analyzer(BasicInterpreter()).analyze(Owner, original)
        assertIs<StringBuilder>(loadTestClass(Owner, original).getMethod("create").invoke(null))

        // Isolate exporter scratch lifetimes from BasicInterpreter's existing opaque reference
        // frame types in JvmSSAImporter. Build the equivalent, precisely typed SSA explicitly.
        val context = JvmSSAImportContext()
        val ids = context.ids
        val type = context.types.objectType("java/lang/StringBuilder", nullable = false)
        val allocation = SSABlock(ids.blockId())
        val initialize = SSABlock(ids.blockId())
        val returned = SSABlock(ids.blockId())
        val value = SSAInstructionResult(ids.valueId(), type)
        val initArg = SSABlockArg(ids.valueId(), 0, type, SSABlockArgOrigin.FrontendState("local", 0))
        val returnArg = SSABlockArg(ids.valueId(), 0, type, SSABlockArgOrigin.FrontendState("local", 0))
        initialize.args += initArg
        returned.args += returnArg
        allocation.append(SSAAllocateInstruction(value, SSAAllocation.Object(type)))
        allocation.terminator = SSAJumpTerminator(SSASuccessor(initialize, listOf(value)))
        val constructor = context.externalRef(SSAExternalRefKind.Function, "java/lang/StringBuilder.<init>()V")
        context.metadata.methods[constructor] = JvmMethodMetadata(
            Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false
        )
        initialize.append(SSACallInstruction(
            null, SSAExternalFunctionRef(constructor, listOf(type), SSAVoidType), listOf(initArg), SSACallDispatch.Direct
        ))
        initialize.terminator = SSAJumpTerminator(SSASuccessor(returned, listOf(initArg)))
        returned.terminator = SSAReturnTerminator(returnArg)
        val function = SSAFunction(
            SSAFunctionSymbol(ids.symbolId(), Owner + ".create()Ljava/lang/StringBuilder;", emptyList(), type),
            mutableListOf(), mutableListOf(allocation, initialize, returned), allocation
        )
        SSAVerifier.verify(function).requireValid()
        val exported = JvmSSAExporter(context.metadata).export(function)
        assertEquals(2 + 1 + 16, exported.maxLocals)
        Analyzer(BasicInterpreter()).analyze(Owner, exported)
        assertIs<StringBuilder>(loadTestClass(Owner, exported).getMethod("create").invoke(null))
    }

    @Test
    fun exceptionBoundsKeepTheLegacySpanAndHandlerOrder() {
        val ids = SSAIdAllocator()
        val blocks = MutableList(4) { SSABlock(ids.blockId()) }
        blocks[0].terminator = SSAJumpTerminator(SSASuccessor(blocks[1]))
        blocks[1].append(SSABinaryInstruction(
            SSAInstructionResult(ids.valueId(), SSAI32Type), SSABinaryOp.Div, SSAIntLiteral(1), SSAIntLiteral(0)
        ))
        blocks[1].terminator = SSAJumpTerminator(SSASuccessor(blocks[2]))
        blocks[2].terminator = SSAReturnTerminator(SSAIntLiteral(1))
        blocks[3].args += SSABlockArg(ids.valueId(), 0, SSARefType(), SSABlockArgOrigin.ExceptionObject)
        blocks[3].terminator = SSAReturnTerminator(SSAIntLiteral(-1))
        val function = SSAFunction(
            SSAFunctionSymbol(ids.symbolId(), Owner + ".guard()I", emptyList(), SSAI32Type),
            mutableListOf(), blocks, blocks[0],
            mutableListOf(SSAExceptionRegion(linkedSetOf(blocks[2], blocks[0]), blocks[3]))
        )
        SSAVerifier.verify(function).requireValid()
        val exported = JvmSSAExporter().export(function)
        val labels = exported.instructions.toArray().filterIsInstance<LabelNode>()
        val region = exported.tryCatchBlocks.single()
        assertSame(labels[0], region.start)
        assertSame(labels[5], region.end)
        assertSame(labels[6], region.handler)
        // Noncontiguous protected sets intentionally retain the pre-existing first..last span.
        assertEquals(-1, loadTestClass(Owner, exported).getMethod("guard").invoke(null))
    }

    @Test
    fun wholeFunctionCarriersKeepArgumentCorrespondenceAndDeterministicIds() {
        fun flattened(): SSAFunction {
            val function = swapFunction(16, 0)
            val originalBlocks = function.blocks.toList()
            val originalArgs = originalBlocks.flatMap { it.args }.toList()
            assertTrue(SSAControlFlowFlattener().flatten(function).changed)
            SSAVerifier.verify(function).requireValid()
            val dispatcher = function.blocks.single { it.terminator is SSASwitchTerminator }
            val cases = (dispatcher.terminator as SSASwitchTerminator).cases
            assertEquals(originalBlocks, cases.map { it.target.block })
            assertEquals(originalArgs.size + 1, dispatcher.args.size)
            val carriers = originalArgs.zip(dispatcher.args.drop(1)).toMap()
            for (case in cases) assertEquals(case.target.block.args.map { carriers.getValue(it) }, case.target.args)
            return function
        }
        val first = flattened()
        val second = flattened()
        assertEquals(first.blocks.map { it.id to it.args.map { arg -> arg.id } }, second.blocks.map { it.id to it.args.map { arg -> arg.id } })
        val exported = JvmSSAExporter().export(first)
        Analyzer(BasicInterpreter()).analyze(Owner, exported)
        val method = loadTestClass(Owner, exported).getMethod(
            "swap", Long::class.javaPrimitiveType, Double::class.javaPrimitiveType, Any::class.java,
            Long::class.javaPrimitiveType, Double::class.javaPrimitiveType, Any::class.java
        )
        assertEquals(37L, method.invoke(null, 11L, 2.5, Any(), 37L, 8.25, Any()))
    }

    private fun swapFunction(blockCount: Int, returned: Int): SSAFunction {
        val ids = SSAIdAllocator()
        val types = listOf(SSAI64Type, SSAF64Type, SSARefType(), SSAI64Type, SSAF64Type, SSARefType())
        val parameters = types.mapIndexed { index, type -> SSAParameter(ids.valueId(), index, type) }
        val slots = listOf(0, 2, 4, 5, 7, 9)
        val entry = SSABlock(ids.blockId())
        val blocks = List(blockCount) {
            SSABlock(ids.blockId()).also { block ->
                types.forEachIndexed { index, type ->
                    block.args += SSABlockArg(ids.valueId(), index, type, SSABlockArgOrigin.FrontendState("local", slots[index]))
                }
            }
        }
        entry.terminator = SSAJumpTerminator(SSASuccessor(blocks.first(), parameters))
        blocks.zipWithNext().forEach { (from, to) ->
            from.terminator = SSAJumpTerminator(SSASuccessor(to, from.args.drop(3) + from.args.take(3)))
        }
        blocks.last().terminator = SSAReturnTerminator(blocks.last().args[returned])
        val descriptor = "(JDLjava/lang/Object;JDLjava/lang/Object;)" + listOf("J", "D", "Ljava/lang/Object;")[returned]
        return SSAFunction(
            SSAFunctionSymbol(ids.symbolId(), Owner + ".swap" + descriptor, types, types[returned]),
            parameters.toMutableList(), (listOf(entry) + blocks).toMutableList(), entry
        )
    }

    private companion object {
        const val Owner = "example/SsaExportPerf"
    }
}