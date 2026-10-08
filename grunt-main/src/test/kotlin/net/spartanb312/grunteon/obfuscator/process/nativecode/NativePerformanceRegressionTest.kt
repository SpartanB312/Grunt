package net.spartanb312.grunteon.obfuscator.process.nativecode

import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmIrImporter
import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmSupportAnalyzer
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativePerformanceRegressionTest {
    @Test
    fun longShallowMethodUsesActualMaxsAndRestoresOriginalMetadata() {
        val method = MethodNode(Opcodes.ACC_PUBLIC, "shallow", "()I", null, null).apply {
            repeat(4096) { instructions.add(InsnNode(Opcodes.NOP)) }
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 8192
            maxLocals = 128
        }
        val ir = NativeJvmIrImporter.import("test/Shallow", method)
        val prepared = NativeJvmCppMethodTranslator.validate(method, ir, NativeJvmSupportAnalyzer.analyze(ir))
        val source = prepared.render("shallow", NativeReferenceSlots(), NativeJvmIntrinsicStats())
        assertContains(source, "jvalue cstack[1]")
        assertContains(source, "jvalue clocal[1]")
        assertEquals(8192, method.maxStack)
        assertEquals(128, method.maxLocals)
    }

    @Test
    fun analysisRecomputesUnderestimatedCategoryTwoMaxs() {
        val method = MethodNode(Opcodes.ACC_PUBLIC, "wide", "(J)J", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.LLOAD, 1))
            instructions.add(InsnNode(Opcodes.LCONST_1))
            instructions.add(InsnNode(Opcodes.LADD))
            instructions.add(InsnNode(Opcodes.LRETURN))
            maxStack = 0
            maxLocals = 0
        }
        val ir = NativeJvmIrImporter.import("test/Wide", method)
        val prepared = NativeJvmCppMethodTranslator.validate(method, ir, NativeJvmSupportAnalyzer.analyze(ir))
        val source = prepared.render("wide", NativeReferenceSlots(), NativeJvmIntrinsicStats())
        assertContains(source, "jvalue cstack[4]")
        assertContains(source, "jvalue clocal[3]")
        assertEquals(0, method.maxStack)
        assertEquals(0, method.maxLocals)
    }

    @Test
    fun preparedMethodsRebindReferencesWithoutRewritingStringConstants() {
        val methods = (0 until 17).map { index ->
            candidate(MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "text", "()Ljava/lang/String;", null, null).apply {
                instructions.add(LdcInsnNode("grt_validate grt_ref_string_0 $index"))
                instructions.add(InsnNode(Opcodes.ARETURN))
                maxStack = 1
            }, "test/Prepared$index")
        }
        val (accepted, skipped) = NativeValidator.validate(methods, NativeBackend.Cpp)
        assertTrue(skipped.isEmpty())
        assertTrue(accepted.all { it.prepared != null })
        val split = NativeCppBackend.generate(accepted, NativePipelineConfig(maxMethodsPerSourceFile = 1)) { false }
        val single = NativeCppBackend.generate(accepted, NativePipelineConfig(splitSourceFiles = false)) { false }
        assertEquals(17, split.plan.referenceSlots.stringSlotCount)
        assertEquals(single.sourceText, split.sourceText)
        val chunks = split.sourceFiles.filter { it.path.fileName.toString().startsWith("grunteon_native_chunk_") }
        assertEquals(17, chunks.size)
        chunks.forEach { chunk ->
            assertContains(chunk.text, "constexpr jint grt_ref_string_0 = ")
            assertContains(chunk.text, "grt_validate grt_ref_string_0 ")
            assertFalse(chunk.text.contains(" JNICALL grt_validate("))
        }
    }

    @Test
    fun preparedIntrinsicStatisticsAreCountedOnceForSplitAndDiagnosticOutput() {
        val methods = (0 until 17).map { index ->
            candidate(MethodNode(Opcodes.ACC_PUBLIC, "abs", "(I)I", null, null).apply {
                instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
                instructions.add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(I)I", false))
                instructions.add(InsnNode(Opcodes.IRETURN))
                maxStack = 1
                maxLocals = 2
            }, "test/Stats$index")
        }
        val accepted = NativeValidator.validate(methods, NativeBackend.Cpp).first
        val split = NativeCppBackend.generate(accepted, NativePipelineConfig(maxMethodsPerSourceFile = 1)) { false }
        assertEquals(17, split.intrinsicStats.total)
        assertContains(split.sourceText, "grt_math_abs_i32")
        assertEquals(17, split.intrinsicStats.total)
        val disabled = NativeCppBackend.generate(accepted, NativePipelineConfig(enablePrimitiveIntrinsics = false)) { false }
        assertEquals(0, disabled.intrinsicStats.total)
        assertContains(disabled.sourceText, "grt_get_method_id(env, ownerClass_")
    }

    @Test
    fun diagnosticSourceIsLazyAndCopyPreservesTheContract() {
        val base = NativeCppBackend.generate(emptyList(), NativePipelineConfig()) { false }
        var calls = 0
        val bundle = NativeSourceBundle(
            plan = base.plan, sourceText = "", sourcePath = Path.of("diagnostic.cpp"),
            libraryPath = base.libraryPath, sourceFiles = base.sourceFiles,
            sourceTextFactory = { calls++; "diagnostic" }
        )
        val copy = bundle.copy(sourceFiles = base.sourceFiles)
        assertEquals(0, calls)
        assertEquals(base.sourceFiles, copy.sourceFiles)
        assertEquals("diagnostic", copy.sourceText)
        assertEquals("diagnostic", bundle.sourceText)
        assertEquals(1, calls)
    }

    @Test
    fun runtimeUsesHandleIdentitySetAndPrunesClearedMemberReferences() {
        val source = NativeCppBackend.generate(emptyList(), NativePipelineConfig()) { false }.sourceText
        assertContains(source, "using GrtLocalRefs = std::unordered_set<jobject>;")
        assertContains(source, "refs.insert(ref);")
        assertContains(source, "refs.erase(ref);")
        assertContains(source, "env->GetObjectRefType(ref) != JNILocalRefType")
        assertFalse(source.contains("for (jobject existing : refs)"))
        assertContains(source, "grt_prune_member_entries(env, methodSlot.entries);")
        assertContains(source, "grt_prune_member_entries(env, fieldSlot.entries);")
        assertContains(source, "env->DeleteWeakGlobalRef(entry.clazz);")
    }

    @Test
    fun freshIntFillHasBoundedBatchingAndAllOtherShapesFallBack() {
        val method = NativePerformanceFixtures.freshIntFill()
        val source = NativePrimitiveArrayLoop.translateOrNull(method, "fill", NativeMethodCommitKind.Direct)
        assertNotNull(source)
        assertContains(source, "jint values[256]")
        assertContains(source, "SetIntArrayRegion(result, offset, count, values)")
        assertFalse(source.contains("PrimitiveArrayCritical"))
        assertNull(NativePrimitiveArrayLoop.translateOrNull(method, "fill", NativeMethodCommitKind.InterfaceProxy))
        val differentValue = NativePerformanceFixtures.freshIntFill()
        differentValue.instructions.toArray().filterIsInstance<VarInsnNode>().last { it.opcode == Opcodes.ILOAD }.`var` = 3
        assertNull(NativePrimitiveArrayLoop.translateOrNull(differentValue, "fill", NativeMethodCommitKind.Direct))
        val callback = NativePerformanceFixtures.freshIntFill()
        callback.instructions.insert(InsnNode(Opcodes.NOP))
        assertNull(NativePrimitiveArrayLoop.translateOrNull(callback, "fill", NativeMethodCommitKind.Direct))
        val wrongTarget = NativePerformanceFixtures.freshIntFill()
        val jumps = wrongTarget.instructions.toArray().filterIsInstance<JumpInsnNode>()
        jumps.last().label = jumps.first().label
        assertNull(NativePrimitiveArrayLoop.translateOrNull(wrongTarget, "fill", NativeMethodCommitKind.Direct))
    }

    private fun candidate(method: MethodNode, owner: String): NativeCandidate = NativeCandidate(
        ClassNode().apply {
            version = Opcodes.V1_8
            access = Opcodes.ACC_PUBLIC
            name = owner
            superName = "java/lang/Object"
            methods.add(method)
        }, method, NativeCandidateSource.MethodAnnotation
    )
}

internal object NativePerformanceFixtures {
    fun freshIntFill(): MethodNode {
        val loop = LabelNode()
        val done = LabelNode()
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "fill", "(II)[I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT))
            instructions.add(VarInsnNode(Opcodes.ASTORE, 2))
            instructions.add(InsnNode(Opcodes.ICONST_0))
            instructions.add(VarInsnNode(Opcodes.ISTORE, 3))
            instructions.add(loop)
            instructions.add(VarInsnNode(Opcodes.ILOAD, 3))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(JumpInsnNode(Opcodes.IF_ICMPGE, done))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 2))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 3))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IASTORE))
            instructions.add(IincInsnNode(3, 1))
            instructions.add(JumpInsnNode(Opcodes.GOTO, loop))
            instructions.add(done)
            instructions.add(VarInsnNode(Opcodes.ALOAD, 2))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 3
            maxLocals = 4
        }
    }
}