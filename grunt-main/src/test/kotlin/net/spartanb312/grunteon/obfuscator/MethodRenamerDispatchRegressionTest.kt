package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.transformers.rename.MethodRenamer
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*
import java.io.StringWriter
import kotlin.test.*

class MethodRenamerDispatchRegressionTest {
    @Test
    fun syntheticDiamondCovariantBridgePrivateAndStaticDispatchSurviveRename() {
        var firstMapping: String? = null
        repeat(2) { run ->
            val nodes = fixture()
            withGeneratedTransformerClasses(if (run == 0) nodes else nodes.reversed(), listOf(MethodRenamer.Config())) { instance ->
                val mapping = instance.nameMapping
                val a = mapping.mapMethodName("dispatch/A", "cancel", "()I")
                val b = mapping.mapMethodName("dispatch/B", "cancel", "()I")
                val impl = mapping.mapMethodName("dispatch/Impl", "cancel", "()I")
                assertEquals(a, b)
                assertEquals(a, impl)
                assertNotEquals("cancel", a)
                val entryName = mapping.mapMethodName("dispatch/Probe", "run", "()I")!!
                val loader = generatedClassLoader(instance.workRes.inputClassCollection)
                assertEquals(76, loader.loadClass("dispatch.Probe").getMethod(entryName).invoke(null))
                val json = StringWriter().also(mapping::dump).toString()
                if (firstMapping == null) firstMapping = json else assertEquals(firstMapping, json)
            }
        }
    }

    private fun fixture(): List<ClassNode> {
        val a = iface("dispatch/A", "cancel", "()I", synthetic = true)
        val b = iface("dispatch/B", "cancel", "()I", synthetic = true)
        val generic = iface("dispatch/Generic", "value", "()Ljava/lang/Object;")
        val base = owner("dispatch/Base").apply {
            methods.add(constantMethod("privateValue", 17, ACC_PRIVATE))
            methods.add(constantMethod("staticValue", 19, ACC_PUBLIC or ACC_STATIC))
            methods.add(privateCaller(name, "basePrivate"))
        }
        val impl = owner("dispatch/Impl", "dispatch/Base").apply {
            interfaces.addAll(listOf(a.name, b.name, generic.name))
            methods.add(constantMethod("cancel", 7, ACC_PUBLIC or ACC_SYNTHETIC or ACC_BRIDGE))
            methods.add(constantMethod("privateValue", 11, ACC_PRIVATE))
            methods.add(constantMethod("staticValue", 13, ACC_PUBLIC or ACC_STATIC))
            methods.add(privateCaller(name, "implPrivate"))
            methods.add(MethodNode(ACC_PUBLIC, "value", "()Ljava/lang/String;", null, null).apply {
                instructions.add(LdcInsnNode("ok"))
                instructions.add(InsnNode(ARETURN))
                maxStack = 1
                maxLocals = 1
            })
            methods.add(MethodNode(ACC_PUBLIC or ACC_SYNTHETIC or ACC_BRIDGE, "value", "()Ljava/lang/Object;", null, null).apply {
                instructions.add(VarInsnNode(ALOAD, 0))
                instructions.add(MethodInsnNode(INVOKEVIRTUAL, "dispatch/Impl", "value", "()Ljava/lang/String;", false))
                instructions.add(InsnNode(ARETURN))
                maxStack = 1
                maxLocals = 1
            })
        }
        val probe = owner("dispatch/Probe").apply {
            methods.add(MethodNode(ACC_PUBLIC or ACC_STATIC, "run", "()I", null, null).apply {
                instructions.add(TypeInsnNode(NEW, impl.name))
                instructions.add(InsnNode(DUP))
                instructions.add(MethodInsnNode(INVOKESPECIAL, impl.name, "<init>", "()V", false))
                instructions.add(VarInsnNode(ASTORE, 0))
                for ((index, iface) in listOf(a, b).withIndex()) {
                    instructions.add(VarInsnNode(ALOAD, 0))
                    instructions.add(MethodInsnNode(INVOKEINTERFACE, iface.name, "cancel", "()I", true))
                    if (index != 0) instructions.add(InsnNode(IADD))
                }
                instructions.add(VarInsnNode(ALOAD, 0))
                instructions.add(MethodInsnNode(INVOKEINTERFACE, generic.name, "value", "()Ljava/lang/Object;", true))
                instructions.add(TypeInsnNode(CHECKCAST, "java/lang/String"))
                instructions.add(MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false))
                instructions.add(InsnNode(IADD))
                for (type in listOf(base, impl)) {
                    instructions.add(MethodInsnNode(INVOKESTATIC, type.name, "staticValue", "()I", false))
                    instructions.add(InsnNode(IADD))
                }
                for ((type, name) in listOf(base to "basePrivate", impl to "implPrivate")) {
                    instructions.add(VarInsnNode(ALOAD, 0))
                    instructions.add(MethodInsnNode(INVOKEVIRTUAL, type.name, name, "()I", false))
                    instructions.add(InsnNode(IADD))
                }
                instructions.add(InsnNode(IRETURN))
                maxStack = 2
                maxLocals = 1
            })
        }
        return listOf(a, b, generic, base, impl, probe)
    }

    private fun owner(name: String, superName: String = "java/lang/Object") = ClassNode().apply {
        version = V1_8
        access = ACC_PUBLIC
        this.name = name
        this.superName = superName
        methods.add(MethodNode(ACC_PUBLIC, "<init>", "()V", null, null).apply {
            instructions.add(VarInsnNode(ALOAD, 0))
            instructions.add(MethodInsnNode(INVOKESPECIAL, superName, "<init>", "()V", false))
            instructions.add(InsnNode(RETURN))
            maxStack = 1
            maxLocals = 1
        })
    }

    private fun iface(name: String, method: String, desc: String, synthetic: Boolean = false) = ClassNode().apply {
        version = V1_8
        access = ACC_PUBLIC or ACC_INTERFACE or ACC_ABSTRACT
        this.name = name
        superName = "java/lang/Object"
        methods.add(MethodNode(ACC_PUBLIC or ACC_ABSTRACT or (if (synthetic) ACC_SYNTHETIC else 0), method, desc, null, null))
    }

    private fun constantMethod(name: String, value: Int, access: Int) = MethodNode(access, name, "()I", null, null).apply {
        instructions.add(IntInsnNode(BIPUSH, value))
        instructions.add(InsnNode(IRETURN))
        maxStack = 1
        maxLocals = if (access and ACC_STATIC == 0) 1 else 0
    }

    private fun privateCaller(owner: String, name: String) = MethodNode(ACC_PUBLIC, name, "()I", null, null).apply {
        instructions.add(VarInsnNode(ALOAD, 0))
        instructions.add(MethodInsnNode(INVOKESPECIAL, owner, "privateValue", "()I", false))
        instructions.add(InsnNode(IRETURN))
        maxStack = 1
        maxLocals = 1
    }
}
