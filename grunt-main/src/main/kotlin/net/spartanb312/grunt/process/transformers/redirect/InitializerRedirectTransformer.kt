package net.spartanb312.grunt.process.transformers.redirect

import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.misc.NativeCandidateTransformer
import net.spartanb312.grunt.utils.builder.*
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.getLoadType
import net.spartanb312.grunt.utils.getRandomString
import net.spartanb312.grunt.utils.isNotExcludedIn
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

/**
 * Redirect initializer
 */
object InitializerRedirectTransformer : Transformer("InitializerRedirect", Category.Redirect) {

    private val exclusions by value("Exclusions", listOf())
    private val nativeUpCall by value("NativeUpCall", false)

    override fun ResourceCache.transform() {
        Logger.info(" - Redirecting initializer...")
        val count = count {
            nonExcluded.asSequence()
                .filter { it.name.isNotExcludedIn(exclusions) }
                .forEach { classNode ->
                    classNode.methods.toList().asSequence()
                        .filter { it.name != "<init>" && it.name != "<clinit>" }
                        .forEach { methodNode ->
                            loop@ for (it in methodNode.instructions.toList()) {
                                val next = it.next ?: continue@loop
                                var nextNext = next.next ?: continue@loop
                                if (it is TypeInsnNode
                                    && it.opcode == Opcodes.NEW
                                    && next.opcode == Opcodes.DUP
                                ) {
                                    val byteCodes = mutableListOf<AbstractInsnNode>()
                                    if (nextNext.opcode != Opcodes.INVOKESPECIAL) {
                                        exit@ while (true) {
                                            val nextNextNext = nextNext.next
                                            if (nextNextNext != null) {
                                                nextNext = nextNextNext
                                                if (nextNext.opcode != Opcodes.INVOKESPECIAL) {
                                                    byteCodes.add(nextNext)
                                                } else break@exit
                                            } else continue@loop
                                        }
                                    }

                                    if (byteCodes.any { it.opcode == Opcodes.NEW || it.opcode == Opcodes.DUP }) continue@loop

                                    val methodInsn = nextNext as MethodInsnNode
                                    if (methodInsn.name != "<init>") continue@loop

                                    methodNode.instructions.remove(it) // Remove NEW
                                    methodNode.instructions.remove(next) // Remove DUP
                                    // Keep loads
                                    // Remove InvokeSpecial later

                                    val methodName = "init_${it.desc.substringAfterLast("/")}${getRandomString(3)}"
                                    val genMethod = genMethod(methodName, methodInsn.desc, it.desc)
                                    val call = MethodInsnNode(
                                        Opcodes.INVOKESTATIC,
                                        classNode.name,
                                        methodName,
                                        genMethod.desc,
                                    )

                                    if (NativeCandidateTransformer.enabled && nativeUpCall) {
                                        genMethod.visitAnnotation(NativeCandidateTransformer.nativeAnnotation, false)
                                        NativeCandidateTransformer.appendedMethods.add(genMethod)
                                    }

                                    classNode.methods.add(genMethod)
                                    methodNode.instructions.set(nextNext, call) // Remove InvokeSpecial
                                    add(1)
                                }
                            }
                        }
                }
        }.get()
        Logger.info("    Redirected $count initializer")
    }

    private fun genMethod(name: String, parametersType: String, returnType: String): MethodNode = method(
        Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC,
        name,
        "${parametersType.removeSuffix("V")}L$returnType;",
        null,
        null
    ) {
        InsnList {
            NEW(returnType)
            DUP
            // LOAD Parameters
            var stack = 0
            Type.getArgumentTypes(methodNode.desc).forEach {
                VAR(it.getLoadType(), stack)
                stack += it.size
            }
            INVOKESPECIAL(returnType, "<init>", parametersType)
            ARETURN
        }
    }

}