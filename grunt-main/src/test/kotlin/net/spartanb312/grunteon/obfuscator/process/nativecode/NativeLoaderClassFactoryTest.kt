package net.spartanb312.grunteon.obfuscator.process.nativecode

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.VarInsnNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeLoaderClassFactoryTest {

    @Test
    fun loaderClosesNativeLibraryResourceOnCopyFailure() {
        val loader = NativeLoaderClassFactory.create(
            loaderInternalName = "test/NativeLoader",
            resourcePath = "/grunteon/native/windows/x64/grunteon_native.dll",
            librarySuffix = ".dll"
        )
        val load = loader.methods.single { it.name == "load" && it.desc == "()V" }

        assertEquals(Opcodes.V1_6, loader.version)
        assertEquals(1, load.tryCatchBlocks.size)
        assertEquals("java/lang/Throwable", load.tryCatchBlocks.single().type)
        assertTrue(load.maxLocals >= 5)

        val instructions = load.instructions.toArray().toList()
        val handlerIndex = instructions.indexOf(load.tryCatchBlocks.single().handler)
        assertTrue(handlerIndex >= 0)
        val handlerInstructions = instructions.drop(handlerIndex)

        assertNotNull(handlerInstructions.filterIsInstance<VarInsnNode>().firstOrNull {
            it.opcode == Opcodes.ASTORE && it.`var` == 4
        })
        assertNotNull(handlerInstructions.filterIsInstance<MethodInsnNode>().firstOrNull {
            it.owner == "java/io/InputStream" && it.name == "close" && it.desc == "()V"
        })
        assertNotNull(handlerInstructions.filterIsInstance<JumpInsnNode>().firstOrNull {
            it.opcode == Opcodes.IFNULL
        })
        assertNotNull(handlerInstructions.filterIsInstance<InsnNode>().firstOrNull {
            it.opcode == Opcodes.ATHROW
        })

        val firstClose = instructions.indexOfFirst {
            it is MethodInsnNode && it.owner == "java/io/InputStream" && it.name == "close"
        }
        val systemLoad = instructions.indexOfFirst {
            it is MethodInsnNode && it.owner == "java/lang/System" && it.name == "load"
        }
        assertTrue(firstClose >= 0)
        assertTrue(systemLoad > firstClose)
    }
}
