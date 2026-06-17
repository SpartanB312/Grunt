package net.spartanb312.grunteon.obfuscator.process.transformers.nativecode

import net.spartanb312.grunteon.obfuscator.process.nativecode.NativeBackend
import net.spartanb312.grunteon.obfuscator.process.nativecode.NativeCandidate
import net.spartanb312.grunteon.obfuscator.process.nativecode.NativeCandidateSource
import net.spartanb312.grunteon.obfuscator.process.nativecode.NativeLoweringKind
import net.spartanb312.grunteon.obfuscator.process.nativecode.NativeValidator
import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmFeature
import net.spartanb312.grunteon.obfuscator.util.NATIVE_EXCLUDED
import net.spartanb312.grunteon.obfuscator.util.NATIVE_JVM_BRIDGE
import net.spartanb312.grunteon.obfuscator.util.extensions.hasAnnotation
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativePreProcessorTest {

    @Test
    fun bridgesInvokeDynamicThroughExcludedSameClassHelper() {
        val method = indyMethod()
        val clazz = ClassNode().apply {
            version = Opcodes.V1_8
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER
            name = "test/IndyOwner"
            superName = "java/lang/Object"
            methods.add(method)
        }

        val result = NativePreProcessor().bridgeInvokeDynamics(clazz, NativePreProcessor.Config())

        assertEquals(1, result.indyCount)
        assertEquals(0, result.constantDynamicCount)
        assertEquals(1, result.helperCount)
        assertFalse(method.instructions.toArray().any { it is InvokeDynamicInsnNode })

        val call = method.instructions.toArray().filterIsInstance<MethodInsnNode>().single()
        assertEquals(Opcodes.INVOKESTATIC, call.opcode)
        assertEquals("test/IndyOwner", call.owner)
        assertEquals("(I)Ljava/lang/String;", call.desc)
        assertFalse(call.itf)

        val helper = clazz.methods.single { it.name == call.name }
        assertTrue(helper.hasAnnotation(NATIVE_EXCLUDED))
        assertTrue(helper.hasAnnotation(NATIVE_JVM_BRIDGE))
        assertNotNull(helper.instructions.toArray().filterIsInstance<InvokeDynamicInsnNode>().singleOrNull())
    }

    @Test
    fun bridgesConstantDynamicThroughExcludedSameClassHelper() {
        val method = condyMethod()
        val clazz = ClassNode().apply {
            version = Opcodes.V11
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER
            name = "test/CondyOwner"
            superName = "java/lang/Object"
            methods.add(method)
        }

        val result = NativePreProcessor().bridgeInvokeDynamics(clazz, NativePreProcessor.Config())

        assertEquals(0, result.indyCount)
        assertEquals(1, result.constantDynamicCount)
        assertEquals(1, result.helperCount)
        assertFalse(method.instructions.toArray().any { it.constantDynamicOrNull() != null })

        val call = method.instructions.toArray().filterIsInstance<MethodInsnNode>().single()
        assertEquals(Opcodes.INVOKESTATIC, call.opcode)
        assertEquals("test/CondyOwner", call.owner)
        assertEquals("()I", call.desc)

        val helper = clazz.methods.single { it.name == call.name }
        assertTrue(helper.hasAnnotation(NATIVE_EXCLUDED))
        assertTrue(helper.hasAnnotation(NATIVE_JVM_BRIDGE))
        assertNotNull(helper.instructions.toArray().singleOrNull { it.constantDynamicOrNull() != null })
    }

    @Test
    fun validatorAcceptsCandidateAfterConstantDynamicBridge() {
        val method = condyMethod()
        val clazz = ClassNode().apply {
            version = Opcodes.V11
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER
            name = "test/CondyCandidate"
            superName = "java/lang/Object"
            methods.add(method)
        }

        NativePreProcessor().bridgeInvokeDynamics(clazz, NativePreProcessor.Config())

        val (accepted, skipped) = NativeValidator.validate(
            listOf(NativeCandidate(clazz, method, NativeCandidateSource.MethodAnnotation)),
            NativeBackend.Cpp
        )

        assertTrue(skipped.isEmpty())
        assertEquals(1, accepted.size)
        assertEquals(NativeLoweringKind.FullJvm, accepted.single().lowering)
        assertFalse(NativeJvmFeature.ConstantDynamic in accepted.single().fullJvmSupport.features)
    }

    @Test
    fun bridgesMethodHandleInvokeThroughExcludedSameClassHelper() {
        val method = methodHandleInvokeMethod()
        val clazz = ClassNode().apply {
            version = Opcodes.V11
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER
            name = "test/MethodHandleOwner"
            superName = "java/lang/Object"
            methods.add(method)
        }

        val result = NativePreProcessor().bridgeInvokeDynamics(clazz, NativePreProcessor.Config())

        assertEquals(0, result.indyCount)
        assertEquals(0, result.constantDynamicCount)
        assertEquals(1, result.methodHandleInvokeCount)
        assertEquals(1, result.helperCount)

        val call = method.instructions.toArray().filterIsInstance<MethodInsnNode>().single()
        assertEquals(Opcodes.INVOKESTATIC, call.opcode)
        assertEquals("test/MethodHandleOwner", call.owner)
        assertEquals("(Ljava/lang/invoke/MethodHandle;I)Ljava/lang/String;", call.desc)
        assertFalse(call.itf)

        val helper = clazz.methods.single { it.name == call.name }
        assertTrue(helper.hasAnnotation(NATIVE_EXCLUDED))
        assertTrue(helper.hasAnnotation(NATIVE_JVM_BRIDGE))

        val helperInvoke = helper.instructions.toArray().filterIsInstance<MethodInsnNode>().single()
        assertEquals(Opcodes.INVOKEVIRTUAL, helperInvoke.opcode)
        assertEquals("java/lang/invoke/MethodHandle", helperInvoke.owner)
        assertEquals("invokeExact", helperInvoke.name)
        assertEquals("(I)Ljava/lang/String;", helperInvoke.desc)
    }

    @Test
    fun validatorAcceptsCandidateAfterMethodHandleInvokeBridge() {
        val method = methodHandleInvokeMethod()
        val clazz = ClassNode().apply {
            version = Opcodes.V11
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER
            name = "test/MethodHandleCandidate"
            superName = "java/lang/Object"
            methods.add(method)
        }

        NativePreProcessor().bridgeInvokeDynamics(clazz, NativePreProcessor.Config())

        val (accepted, skipped) = NativeValidator.validate(
            listOf(NativeCandidate(clazz, method, NativeCandidateSource.MethodAnnotation)),
            NativeBackend.Cpp
        )

        assertTrue(skipped.isEmpty())
        assertEquals(1, accepted.size)
        assertEquals(NativeLoweringKind.FullJvm, accepted.single().lowering)
    }

    private fun indyMethod(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "format",
            "(I)Ljava/lang/String;",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(
                InvokeDynamicInsnNode(
                    "makeConcatWithConstants",
                    "(I)Ljava/lang/String;",
                    Handle(
                        Opcodes.H_INVOKESTATIC,
                        "java/lang/invoke/StringConcatFactory",
                        "makeConcatWithConstants",
                        "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                        false
                    ),
                    "value=\u0001"
                )
            )
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun condyMethod(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "constant",
            "()I",
            null,
            null
        ).apply {
            instructions.add(
                LdcInsnNode(
                    ConstantDynamic(
                        "dyn",
                        "I",
                        Handle(
                            Opcodes.H_INVOKESTATIC,
                            "test/Bootstrap",
                            "bsm",
                            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/Class;)I",
                            false
                        )
                    )
                )
            )
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 0
        }
    }

    private fun methodHandleInvokeMethod(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "invokeHandle",
            "(Ljava/lang/invoke/MethodHandle;I)Ljava/lang/String;",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(
                MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/invoke/MethodHandle",
                    "invokeExact",
                    "(I)Ljava/lang/String;",
                    false
                )
            )
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun Any.constantDynamicOrNull(): ConstantDynamic? {
        return (this as? LdcInsnNode)?.cst as? ConstantDynamic
    }
}
