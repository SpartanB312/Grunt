package net.spartanb312.grunteon.obfuscator.process.nativecode

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.VarInsnNode

class NativeSsaDirectTranslatorTest {

    @Test
    fun validatorPrefersPrimitiveIntBeforeSsaDirectForStraightLineIntHelpers() {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "add", "(II)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }

        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/SsaAdd", method)),
            NativeBackend.Cpp
        )

        assertEquals(0, skipped.size)
        assertEquals(NativeLoweringKind.PrimitiveInt, accepted.single().lowering)

        val source = NativeCppBackend.generate(
            methods = accepted,
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "uint32_t local0")
        assertContains(source, "uint32_t local1")
        assertContains(source, "static_cast<uint32_t>(arg0)")
        assertContains(source, "static_cast<uint32_t>(arg1)")
        assertContains(source, "return grt_i32(")
    }

    @Test
    fun ssaDirectLowersSignedShiftWithoutNativeSignedRightShift() {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "shift", "(II)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.ISHR))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }

        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/SsaShift", method)),
            NativeBackend.Cpp
        )

        assertEquals(0, skipped.size)
        assertEquals(NativeLoweringKind.SsaDirect, accepted.single().lowering)

        val source = NativeCppBackend.generate(
            methods = accepted,
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "grt_ishr32_bits")
        assertContains(source, "return grt_i32(")
    }

    @Test
    fun ssaDirectLowersSimpleBranchWithBlockArgs() {
        val nonNegative = LabelNode()
        val end = LabelNode()
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "sign", "(I)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(JumpInsnNode(Opcodes.IFGE, nonNegative))
            instructions.add(InsnNode(Opcodes.ICONST_M1))
            instructions.add(JumpInsnNode(Opcodes.GOTO, end))
            instructions.add(nonNegative)
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(end)
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 1
        }

        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/SsaBranch", method)),
            NativeBackend.Cpp
        )

        assertEquals(0, skipped.size)
        assertEquals(NativeLoweringKind.SsaDirect, accepted.single().lowering)

        val source = NativeCppBackend.generate(
            methods = accepted,
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "L_SSA_")
        assertContains(source, "if ((")
        assertContains(source, "edge_")
        assertContains(source, "barg_")
        assertContains(source, "return grt_i32(")
    }

    @Test
    fun ssaDirectLowersTableSwitch() {
        val one = LabelNode()
        val two = LabelNode()
        val default = LabelNode()
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "switcher", "(I)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(TableSwitchInsnNode(1, 2, default, one, two))
            instructions.add(one)
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(two)
            instructions.add(InsnNode(Opcodes.ICONST_2))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(default)
            instructions.add(InsnNode(Opcodes.ICONST_M1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 1
        }

        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/SsaSwitch", method)),
            NativeBackend.Cpp
        )

        assertEquals(0, skipped.size)
        assertEquals(NativeLoweringKind.SsaDirect, accepted.single().lowering)

        val source = NativeCppBackend.generate(
            methods = accepted,
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "switch (")
        assertContains(source, "case 0x00000001U")
        assertContains(source, "case 0x00000002U")
        assertContains(source, "default:")
        assertContains(source, "return grt_i32(")
    }

    @Test
    fun primitiveIntHandlesIntegerRotateLeftBeforeSsaDirect() {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "rotate", "(II)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/Integer",
                "rotateLeft",
                "(II)I",
                false
            ))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }

        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/SsaRotate", method)),
            NativeBackend.Cpp
        )

        assertEquals(0, skipped.size)
        assertEquals(NativeLoweringKind.PrimitiveInt, accepted.single().lowering)

        val bundle = NativeCppBackend.generate(
            methods = accepted,
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        )
        val source = bundle.sourceText

        assertContains(source, "grt_rotl32")
        assertEquals(0, bundle.intrinsicStats.total)
        assertEquals(0, bundle.ssaIntrinsicStats.total)
    }

    @Test
    fun ssaDirectLowersLongArithmeticAndReturn() {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "mixLong", "(JJ)J", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.LLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.LLOAD, 2))
            instructions.add(InsnNode(Opcodes.LADD))
            instructions.add(VarInsnNode(Opcodes.LLOAD, 0))
            instructions.add(IntInsnNode(Opcodes.BIPUSH, 7))
            instructions.add(InsnNode(Opcodes.LSHR))
            instructions.add(InsnNode(Opcodes.LXOR))
            instructions.add(InsnNode(Opcodes.LRETURN))
            maxStack = 6
            maxLocals = 4
        }

        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/SsaLong", method)),
            NativeBackend.Cpp
        )

        assertEquals(0, skipped.size)
        assertEquals(NativeLoweringKind.SsaDirect, accepted.single().lowering)

        val source = NativeCppBackend.generate(
            methods = accepted,
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "static jlong JNICALL")
        assertContains(source, "uint64_t v")
        assertContains(source, "static_cast<uint64_t>(arg0)")
        assertContains(source, "grt_lshr64_bits")
        assertContains(source, "return grt_i64(")
    }

    @Test
    fun ssaDirectLowersFloatArithmeticAndReturn() {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "mixFloat", "(FF)F", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.FLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.FLOAD, 1))
            instructions.add(InsnNode(Opcodes.FREM))
            instructions.add(LdcInsnNode(2.5f))
            instructions.add(InsnNode(Opcodes.FADD))
            instructions.add(InsnNode(Opcodes.FRETURN))
            maxStack = 2
            maxLocals = 2
        }

        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/SsaFloat", method)),
            NativeBackend.Cpp
        )

        assertEquals(0, skipped.size)
        assertEquals(NativeLoweringKind.SsaDirect, accepted.single().lowering)

        val source = NativeCppBackend.generate(
            methods = accepted,
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "static jfloat JNICALL")
        assertContains(source, "jfloat v")
        assertContains(source, "std::fmod")
        assertContains(source, "return static_cast<jfloat>(")
    }

    @Test
    fun ssaDirectLowersDoubleCompareIntrinsic() {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "compareDouble", "(DD)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.DLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.DLOAD, 2))
            instructions.add(InsnNode(Opcodes.DCMPG))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 4
            maxLocals = 4
        }

        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/SsaDoubleCompare", method)),
            NativeBackend.Cpp
        )

        assertEquals(0, skipped.size)
        assertEquals(NativeLoweringKind.SsaDirect, accepted.single().lowering)

        val source = NativeCppBackend.generate(
            methods = accepted,
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "static jint JNICALL")
        assertContains(source, "std::isnan")
        assertContains(source, "0xffffffffU")
        assertContains(source, "return grt_i32(")
    }

    @Test
    fun pipelineLowersEveryRegistryIntrinsicWithoutJniCallPath() {
        assertEquals(NativeJvmIntrinsicRegistry.keys, NativeSsaIntrinsicLowerer.supportedKeys)
        val keys = NativeSsaIntrinsicLowerer.supportedKeys
            .sortedWith(compareBy({ it.owner }, { it.name }, { it.desc }))
        val primitiveIntInterceptedKeys = setOf(
            NativeJvmIntrinsicKey(Opcodes.INVOKESTATIC, "java/lang/Integer", "rotateLeft", "(II)I")
        )
        assertContains(keys, primitiveIntInterceptedKeys.single())
        val candidates = keys
            .mapIndexed { index, key ->
                candidate("test/SsaIntrinsic$index", intrinsicMethod(key, "intrinsic$index"))
            }

        val (accepted, skipped) = NativeValidator.validate(candidates, NativeBackend.Cpp)

        assertEquals(0, skipped.size)
        assertEquals(candidates.size, accepted.size)
        accepted.zip(keys).forEach { (acceptedMethod, key) ->
            val expected = if (key in primitiveIntInterceptedKeys) {
                NativeLoweringKind.PrimitiveInt
            } else {
                NativeLoweringKind.SsaDirect
            }
            assertEquals(expected, acceptedMethod.lowering)
        }

        val bundle = NativeCppBackend.generate(
            methods = accepted,
            config = NativePipelineConfig(enabled = true, maxMethodsPerSourceFile = 1),
            classExists = { false }
        )
        val chunkSource = bundle.sourceFiles
            .filter { it.path.fileName.toString().startsWith("grunteon_native_chunk_") }
            .joinToString("\n") { it.text }

        assertFalse("CallStatic" in chunkSource)
        assertFalse("grt_get_method_id" in chunkSource)
        assertFalse("jvalue args_" in chunkSource)
        assertEquals(0, bundle.intrinsicStats.total)
        assertEquals(NativeSsaIntrinsicLowerer.supportedKeys.size - primitiveIntInterceptedKeys.size, bundle.ssaIntrinsicStats.total)
        assertEquals(NativeSsaIntrinsicLowerer.supportedKeys.size - primitiveIntInterceptedKeys.size, bundle.ssaIntrinsicStats.unique)
    }

    @Test
    fun ssaDirectFallsBackForNonRegistryPrimitiveCalls() {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "divideUnsigned", "(II)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/Integer",
                "divideUnsigned",
                "(II)I",
                false
            ))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }

        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/SsaNonRegistry", method)),
            NativeBackend.Cpp
        )

        assertEquals(0, skipped.size)
        assertEquals(NativeLoweringKind.FullJvm, accepted.single().lowering)
    }

    @Test
    fun ssaDirectLowersCheckedIntegerDivRem() {
        val methods = listOf(
            candidate("test/SsaIntDiv", intBinaryMethod("divInt", Opcodes.IDIV)),
            candidate("test/SsaIntRem", intBinaryMethod("remInt", Opcodes.IREM)),
            candidate("test/SsaLongDiv", longBinaryMethod("divLong", Opcodes.LDIV)),
            candidate("test/SsaLongRem", longBinaryMethod("remLong", Opcodes.LREM))
        )

        val (accepted, skipped) = NativeValidator.validate(methods, NativeBackend.Cpp)

        assertEquals(0, skipped.size)
        assertEquals(4, accepted.size)
        accepted.forEach { assertEquals(NativeLoweringKind.SsaDirect, it.lowering) }

        val source = NativeCppBackend.generate(
            methods = accepted,
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "grt_idiv32")
        assertContains(source, "grt_irem32")
        assertContains(source, "grt_idiv64")
        assertContains(source, "grt_irem64")
        assertContains(source, "JNIEnv* env")
    }

    private fun candidate(className: String, method: MethodNode): NativeCandidate {
        return NativeCandidate(
            classNode = org.objectweb.asm.tree.ClassNode().apply {
                version = Opcodes.V1_8
                access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER
                name = className
                superName = "java/lang/Object"
                methods.add(method)
            },
            methodNode = method,
            source = NativeCandidateSource.MethodAnnotation
        )
    }

    private fun intrinsicMethod(key: NativeJvmIntrinsicKey, name: String): MethodNode {
        val argumentTypes = Type.getArgumentTypes(key.desc)
        val returnType = Type.getReturnType(key.desc)
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, name, key.desc, null, null).apply {
            var local = 0
            argumentTypes.forEach { type ->
                instructions.add(VarInsnNode(loadOpcode(type), local))
                local += type.size
            }
            instructions.add(MethodInsnNode(key.opcode, key.owner, key.name, key.desc, false))
            instructions.add(InsnNode(returnOpcode(returnType)))
            maxStack = argumentTypes.sumOf { it.size }.coerceAtLeast(returnType.size).coerceAtLeast(1)
            maxLocals = local
        }
    }

    private fun intBinaryMethod(name: String, opcode: Int): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, name, "(II)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(opcode))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun longBinaryMethod(name: String, opcode: Int): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, name, "(JJ)J", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.LLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.LLOAD, 2))
            instructions.add(InsnNode(opcode))
            instructions.add(InsnNode(Opcodes.LRETURN))
            maxStack = 4
            maxLocals = 4
        }
    }

    private fun loadOpcode(type: Type): Int {
        return when (type.sort) {
            Type.BOOLEAN,
            Type.BYTE,
            Type.SHORT,
            Type.CHAR,
            Type.INT -> Opcodes.ILOAD
            Type.LONG -> Opcodes.LLOAD
            Type.FLOAT -> Opcodes.FLOAD
            Type.DOUBLE -> Opcodes.DLOAD
            else -> error("unsupported test argument type $type")
        }
    }

    private fun returnOpcode(type: Type): Int {
        return when (type.sort) {
            Type.BOOLEAN,
            Type.BYTE,
            Type.SHORT,
            Type.CHAR,
            Type.INT -> Opcodes.IRETURN
            Type.LONG -> Opcodes.LRETURN
            Type.FLOAT -> Opcodes.FRETURN
            Type.DOUBLE -> Opcodes.DRETURN
            else -> error("unsupported test return type $type")
        }
    }
}
