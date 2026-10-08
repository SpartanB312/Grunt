package net.spartanb312.grunt.ir.jvm

import net.spartanb312.grunt.ir.flow.core.FlowVerifier
import net.spartanb312.grunt.ir.flow.jvm.*
import net.spartanb312.grunt.ir.ssa.core.SSAVerifier
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAExporter
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAImporter
import net.spartanb312.grunt.ir.test.loadTestClass
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.Frame
import kotlin.test.*

class JvmImportPerformanceTest {
    @Test
    fun shallowFramesUseDynamicCapacityAndRestoreInputMaxs() {
        // This is an allocation-shape assertion, not a wall-clock benchmark. ASM is version-pinned.
        val values = Frame::class.java.getDeclaredField("values").apply { isAccessible = true }
        for (count in listOf(128, 2048, 8192)) {
            val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "shallow", "()V", null, null)
            repeat(count) { method.instructions.add(InsnNode(Opcodes.NOP)) }
            method.instructions.add(InsnNode(Opcodes.RETURN))
            method.maxStack = 65535
            method.maxLocals = 17
            val result = analyzeJvmFrames("example/ImportPerf", method, BasicInterpreter())
            assertEquals(0, result.maxStack)
            assertEquals(0, result.maxLocals)
            assertEquals(65535, method.maxStack)
            assertEquals(17, method.maxLocals)
            assertTrue(result.frames.filterNotNull().all { (values.get(it) as Array<*>).size <= 8 })
            val flow = JvmFlowImporter().import("example/ImportPerf", method)
            assertEquals(0, flow.metadata.maxStack)
            assertEquals(17, flow.method.locals.nextSlot)
        }
    }

    @Test
    fun staleCategoryTwoStackWorksInBothAnalyzerModes() {
        for (mode in JvmFlowAnalyzerMode.entries) {
            val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "wide", "()V", null, null)
            listOf(Opcodes.LCONST_0, Opcodes.DCONST_0, Opcodes.POP2, Opcodes.POP2, Opcodes.RETURN)
                .forEach { method.instructions.add(InsnNode(it)) }
            method.maxLocals = 7
            method.maxStack = 0
            val imported = JvmFlowImporter(analyzerMode = mode).import("example/ImportPerf", method)
            assertEquals(4, imported.metadata.maxStack)
            assertEquals(0, method.maxStack)
            assertEquals(7, method.maxLocals)
            FlowVerifier.verify(imported.method).requireValid()
            val exported = JvmFlowExporter(imported.metadata).export(imported.method)
            Analyzer(BasicInterpreter()).analyze("example/ImportPerf", exported)
            loadTestClass("example/ImportPerf", exported).getMethod("wide").invoke(null)
        }
    }

    @Test
    fun failedDynamicAnalysisAlsoRestoresMaxs() {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "bad", "()V", null, null)
        method.instructions.add(InsnNode(Opcodes.POP))
        method.instructions.add(InsnNode(Opcodes.RETURN))
        method.maxLocals = 3
        method.maxStack = 19
        assertFails { analyzeJvmFrames("example/ImportPerf", method, BasicInterpreter()) }
        assertEquals(3, method.maxLocals)
        assertEquals(19, method.maxStack)
    }

    @Test
    fun indexesResolveAliasesMetadataAndEndSentinels() {
        val first = LabelNode()
        val alias = LabelNode()
        val end = LabelNode()
        val nodes = InsnList().apply {
            add(first)
            add(LineNumberNode(10, first))
            add(alias)
            add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
            add(InsnNode(Opcodes.RETURN))
            add(end)
        }
        val index = JvmInstructionIndex(nodes)
        assertEquals(0, index.indexOf(first))
        assertEquals(4, index.nextExecutableIndex(index.indexOf(alias)))
        assertEquals(-1, index.indexOf(LabelNode()))
        assertNull(index.nextExecutableIndex(-1))
        assertNull(index.nextExecutableIndex(index.indexOf(end)))
        assertNull(index.nextExecutableIndex(index.size))
        val blocks = JvmBlockIndex(listOf(2, 8, 15)) { it }
        assertEquals(listOf(8), blocks.between(3, 15))
        assertEquals(emptyList(), blocks.between(15, 3))
        assertEquals(15, blocks.atStart(15))
        assertNull(blocks.atStart(16))
    }

    @Test
    fun sharedSwitchTargetsAndAliasesRoundTripThroughBothImports() {
        val even = LabelNode()
        val evenAlias = LabelNode()
        val odd = LabelNode()
        val fallback = LabelNode()
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "pick", "(I)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(LookupSwitchInsnNode(fallback, IntArray(256) { it }, Array(256) {
                when (it % 4) { 0 -> even; 2 -> evenAlias; else -> odd }
            }))
            instructions.add(even)
            instructions.add(LineNumberNode(11, even))
            instructions.add(evenAlias)
            instructions.add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
            instructions.add(IntInsnNode(Opcodes.BIPUSH, 11))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(odd)
            instructions.add(IntInsnNode(Opcodes.BIPUSH, 22))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(fallback)
            instructions.add(InsnNode(Opcodes.ICONST_M1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxLocals = 1
            maxStack = 1
        }
        val flow = JvmFlowImporter().import("example/ImportPerf", method)
        FlowVerifier.verify(flow.method).requireValid()
        assertEquals(3, flow.method.edges.map { it.to }.distinct().size)
        val ssa = JvmSSAImporter().import("example/ImportPerf", method)
        SSAVerifier.verify(ssa.function).requireValid()
        val exported = listOf(JvmFlowExporter(flow.metadata).export(flow.method), JvmSSAExporter(ssa.metadata).export(ssa.function))
        for (result in exported) {
            Analyzer(BasicInterpreter()).analyze("example/ImportPerf", result)
            val pick = loadTestClass("example/ImportPerf", result).getMethod("pick", Int::class.javaPrimitiveType)
            for (key in listOf(-1, 0, 1, 2, 255, 256)) {
                assertEquals(if (key !in 0..255) -1 else if (key % 2 == 0) 11 else 22, pick.invoke(null, key))
            }
        }
    }

    @Test
    fun exceptionEndAtInstructionSentinelKeepsOnlyProtectedBlocks() {
        val handler = LabelNode()
        val start = LabelNode()
        val end = LabelNode()
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "guard", "()I", null, null).apply {
            instructions.add(JumpInsnNode(Opcodes.GOTO, start))
            instructions.add(handler)
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.ICONST_M1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(start)
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(end)
            tryCatchBlocks.add(TryCatchBlockNode(start, end, handler, null))
            maxStack = 1
        }
        val flow = JvmFlowImporter().import("example/ImportPerf", method)
        val ssa = JvmSSAImporter().import("example/ImportPerf", method)
        assertEquals(1, flow.method.exceptionRegions.single().protectedBlocks.size)
        assertEquals(1, ssa.function.exceptionRegions.single().protectedBlocks.size)
        FlowVerifier.verify(flow.method).requireValid()
        SSAVerifier.verify(ssa.function).requireValid()
    }
}