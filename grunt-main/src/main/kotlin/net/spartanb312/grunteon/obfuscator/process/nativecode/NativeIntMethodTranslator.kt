package net.spartanb312.grunteon.obfuscator.process.nativecode

import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import java.util.Locale

internal object NativeIntMethodTranslator {

    fun validate(methodNode: MethodNode) {
        translate(methodNode, "grt_validate")
    }

    fun translate(methodNode: MethodNode, functionName: String): String {
        val argumentTypes = Type.getArgumentTypes(methodNode.desc)
        val returnType = Type.getReturnType(methodNode.desc)
        if (argumentTypes.any { it.sort != Type.INT } || returnType.sort != Type.INT) {
            throw UnsupportedNativeInstruction(
                NativeSkipReason.UnsupportedDescriptor,
                "v1 C++ backend accepts static int-only helpers; descriptor=${methodNode.desc}"
            )
        }

        val maxLocals = maxOf(methodNode.maxLocals, argumentTypes.size)
        val body = StringBuilder()
        body.append("static jint JNICALL ")
            .append(functionName)
            .append("(JNIEnv*, jclass")
        argumentTypes.indices.forEach { index ->
            body.append(", jint arg").append(index)
        }
        body.append(") {\n")
        for (index in 0 until maxLocals) {
            val init = if (index < argumentTypes.size) {
                "static_cast<uint32_t>(arg$index)"
            } else {
                "0u"
            }
            body.append("    uint32_t local").append(index).append(" = ").append(init).append(";\n")
        }

        val stack = mutableListOf<String>()
        var returned = false

        fun push(value: String) {
            stack += value
        }

        fun pop(opcode: String): String {
            if (stack.isEmpty()) {
                throw UnsupportedNativeInstruction(
                    NativeSkipReason.UnsupportedInstruction,
                    "$opcode requires a stack value"
                )
            }
            return stack.removeAt(stack.lastIndex)
        }

        fun binary(opcode: String, operator: String) {
            val right = pop(opcode)
            val left = pop(opcode)
            push("(($left) $operator ($right))")
        }

        fun ensureLocal(index: Int, opcode: String) {
            if (index !in 0 until maxLocals) {
                throw UnsupportedNativeInstruction(
                    NativeSkipReason.UnsupportedInstruction,
                    "$opcode references local $index outside maxLocals=$maxLocals"
                )
            }
        }

        methodNode.instructions?.toArray()?.forEach { insn ->
            when (insn.opcode) {
                -1, Opcodes.NOP -> Unit
                Opcodes.ICONST_M1 -> push(intLiteral(-1))
                Opcodes.ICONST_0 -> push(intLiteral(0))
                Opcodes.ICONST_1 -> push(intLiteral(1))
                Opcodes.ICONST_2 -> push(intLiteral(2))
                Opcodes.ICONST_3 -> push(intLiteral(3))
                Opcodes.ICONST_4 -> push(intLiteral(4))
                Opcodes.ICONST_5 -> push(intLiteral(5))
                Opcodes.IADD -> binary("IADD", "+")
                Opcodes.ISUB -> binary("ISUB", "-")
                Opcodes.IMUL -> binary("IMUL", "*")
                Opcodes.IAND -> binary("IAND", "&")
                Opcodes.IOR -> binary("IOR", "|")
                Opcodes.IXOR -> binary("IXOR", "^")
                Opcodes.INEG -> push("(0u - (${pop("INEG")}))")
                Opcodes.IRETURN -> {
                    val value = pop("IRETURN")
                    body.append("    return grt_i32(").append(value).append(");\n")
                    returned = true
                }
                Opcodes.POP -> {
                    pop("POP")
                }
                Opcodes.DUP -> {
                    val value = pop("DUP")
                    push(value)
                    push(value)
                }
                Opcodes.DUP_X1 -> {
                    val value1 = pop("DUP_X1")
                    val value2 = pop("DUP_X1")
                    push(value1)
                    push(value2)
                    push(value1)
                }
                Opcodes.DUP_X2 -> {
                    val value1 = pop("DUP_X2")
                    val value2 = pop("DUP_X2")
                    val value3 = pop("DUP_X2")
                    push(value1)
                    push(value3)
                    push(value2)
                    push(value1)
                }
                Opcodes.DUP2 -> {
                    val value1 = pop("DUP2")
                    val value2 = pop("DUP2")
                    push(value2)
                    push(value1)
                    push(value2)
                    push(value1)
                }
                Opcodes.SWAP -> {
                    val value1 = pop("SWAP")
                    val value2 = pop("SWAP")
                    push(value1)
                    push(value2)
                }
                Opcodes.MONITORENTER,
                Opcodes.MONITOREXIT -> throw UnsupportedNativeInstruction(
                    NativeSkipReason.Monitor,
                    "monitor instruction is not supported"
                )
                else -> translateTypedInstruction(insn, body, stack, ::push, ::pop, ::ensureLocal)
            }
        } ?: throw UnsupportedNativeInstruction(
            NativeSkipReason.EmptyInstructions,
            "method has no instruction list"
        )

        if (!returned) {
            throw UnsupportedNativeInstruction(
                NativeSkipReason.UnsupportedInstruction,
                "method does not end with an int return in the supported subset"
            )
        }
        body.append("}\n")
        return body.toString()
    }

    private fun translateTypedInstruction(
        insn: AbstractInsnNode,
        body: StringBuilder,
        stack: MutableList<String>,
        push: (String) -> Unit,
        pop: (String) -> String,
        ensureLocal: (Int, String) -> Unit
    ) {
        when (insn) {
            is IntInsnNode -> when (insn.opcode) {
                Opcodes.BIPUSH,
                Opcodes.SIPUSH -> push(intLiteral(insn.operand))
                else -> unsupported(insn)
            }
            is VarInsnNode -> when (insn.opcode) {
                Opcodes.ILOAD -> {
                    ensureLocal(insn.`var`, "ILOAD")
                    push("local${insn.`var`}")
                }
                Opcodes.ISTORE -> {
                    ensureLocal(insn.`var`, "ISTORE")
                    body.append("    local")
                        .append(insn.`var`)
                        .append(" = ")
                        .append(pop("ISTORE"))
                        .append(";\n")
                }
                else -> unsupported(insn)
            }
            is LdcInsnNode -> when (val cst = insn.cst) {
                is Int -> push(intLiteral(cst))
                is ConstantDynamic -> throw UnsupportedNativeInstruction(
                    NativeSkipReason.ConstantDynamic,
                    "constant dynamic is not supported"
                )
                else -> throw UnsupportedNativeInstruction(
                    NativeSkipReason.UnsupportedInstruction,
                    "unsupported LDC constant ${cst?.javaClass?.name ?: "null"}"
                )
            }
            is MethodInsnNode -> {
                if (insn.opcode == Opcodes.INVOKESTATIC &&
                    insn.owner == "java/lang/Integer" &&
                    insn.name == "rotateLeft" &&
                    insn.desc == "(II)I"
                ) {
                    val distance = pop("Integer.rotateLeft")
                    val value = pop("Integer.rotateLeft")
                    push("grt_rotl32($value, $distance)")
                } else {
                    unsupported(insn)
                }
            }
            is InvokeDynamicInsnNode -> throw UnsupportedNativeInstruction(
                NativeSkipReason.InvokeDynamic,
                "invokedynamic is not supported"
            )
            else -> unsupported(insn)
        }
        if (stack.size > 256) {
            throw UnsupportedNativeInstruction(
                NativeSkipReason.UnsupportedInstruction,
                "operand stack grew beyond supported bounds"
            )
        }
    }

    private fun unsupported(insn: AbstractInsnNode): Nothing {
        throw UnsupportedNativeInstruction(
            NativeSkipReason.UnsupportedInstruction,
            "unsupported opcode ${insn.opcode} (${insn.javaClass.simpleName})"
        )
    }

    private fun intLiteral(value: Int): String {
        return String.format(Locale.US, "0x%08xU", value)
    }
}
