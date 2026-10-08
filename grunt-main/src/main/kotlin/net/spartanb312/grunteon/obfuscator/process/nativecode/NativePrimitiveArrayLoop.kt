package net.spartanb312.grunteon.obfuscator.process.nativecode

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode

/**
 * A deliberately closed fast path: a fresh, unescaped int[] filled with one scalar argument.
 * No shared arrays, aliases, callbacks, handlers, partial failing writes, or critical JNI regions.
 * All other array loops retain the ordinary instruction-by-instruction JVM lowering.
 */
internal object NativePrimitiveArrayLoop {
    fun translateOrNull(method: MethodNode, name: String, commitKind: NativeMethodCommitKind): String? {
        if (method.access and Opcodes.ACC_STATIC == 0 || method.desc != "(II)[I" ||
            commitKind != NativeMethodCommitKind.Direct || !method.tryCatchBlocks.isNullOrEmpty()
        ) return null
        val insns = method.instructions.toArray().filter { it.opcode >= 0 }
        val expected = intArrayOf(
            Opcodes.ILOAD, Opcodes.NEWARRAY, Opcodes.ASTORE, Opcodes.ICONST_0, Opcodes.ISTORE,
            Opcodes.ILOAD, Opcodes.ILOAD, Opcodes.IF_ICMPGE, Opcodes.ALOAD, Opcodes.ILOAD,
            Opcodes.ILOAD, Opcodes.IASTORE, Opcodes.IINC, Opcodes.GOTO, Opcodes.ALOAD, Opcodes.ARETURN
        )
        if (insns.size != expected.size || insns.indices.any { insns[it].opcode != expected[it] }) return null
        fun slot(index: Int): Int = (insns[index] as? VarInsnNode)?.`var` ?: -1
        val array = slot(2)
        val index = slot(4)
        if (array < 2 || index < 2 || array == index || slot(0) != 0 ||
            slot(5) != index || slot(6) != 0 || slot(8) != array || slot(9) != index ||
            slot(10) != 1 || slot(14) != array || (insns[1] as IntInsnNode).operand != Opcodes.T_INT
        ) return null
        val increment = insns[12] as IincInsnNode
        if (increment.`var` != index || increment.incr != 1) return null
        fun target(instruction: AbstractInsnNode): AbstractInsnNode? {
            var next: AbstractInsnNode? = (instruction as JumpInsnNode).label
            while (next != null && next.opcode < 0) next = next.next
            return next
        }
        if (target(insns[7]) !== insns[14] || target(insns[13]) !== insns[5]) return null
        return """
            jobject JNICALL $name(JNIEnv* env, jclass, jint arg0, jint arg1) {
                // Bounded fill of a fresh, unescaped primitive array; no JNI critical section.
                jintArray result = env->NewIntArray(arg0);
                if (result == nullptr) return nullptr;
                if (arg1 == 0 || arg0 == 0) return result;
                jint values[256];
                for (jint i = 0; i < 256; ++i) values[i] = arg1;
                for (jint offset = 0; offset < arg0;) {
                    jint count = arg0 - offset < 256 ? arg0 - offset : 256;
                    env->SetIntArrayRegion(result, offset, count, values);
                    if (env->ExceptionCheck()) { env->DeleteLocalRef(result); return nullptr; }
                    offset += count;
                }
                return result;
            }
        """.trimIndent() + "\n"
    }
}