package net.spartanb312.grunt.process.transformers.redirect

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.misc.NativeCandidateTransformer
import net.spartanb312.grunt.utils.*
import net.spartanb312.grunt.utils.builder.*
import net.spartanb312.grunt.utils.extensions.getCallingMethodNodeAndOwner
import net.spartanb312.grunt.utils.extensions.isPrivate
import net.spartanb312.grunt.utils.extensions.setPublic
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import kotlin.random.Random

/**
 * Redirect method calls
 * Last update on 24/07/02
 */
object MethodRedirectTransformer : Transformer("MethodRedirect", Category.Redirect) {

    private val generateOuterClass by setting("GenerateOuterClass", true)
    private val randomCall by setting("RandomCall", true)
    private val excludedClasses by setting("ExcludedClasses", listOf())
    private val excludedMethodName by setting("ExcludedMethodName", listOf())

    private val nativeDownCall by setting("NativeDownCall", false)
    private val nativeUpCall by setting("NativeUpCall", false)

    override fun ResourceCache.transform() {
        Logger.info(" - Redirecting method calls...")
        val newClasses = mutableMapOf<ClassNode, ClassNode>() // Owner Companion
        val count = count {
            nonExcluded.asSequence()
                .filter { it.name.isNotExcludedIn(excludedClasses) }
                .forEach { classNode ->
                    classNode.methods.toList().forEach { methodNode ->
                        methodNode.instructions.toList().forEach {
                            if (it is MethodInsnNode && it.name.isNotExcludedIn(excludedMethodName)) {
                                val pair = it.getCallingMethodNodeAndOwner(this@transform)
                                if (pair != null) {
                                    val callingOwner = pair.first
                                    val callingMethod = pair.second
                                    if (nonExcluded.contains(callingOwner)) {
                                        var shouldOuter = generateOuterClass
                                        // Set accesses
                                        if (shouldOuter) {
                                            if(callingMethod.isPrivate || callingMethod.isPrivate) shouldOuter = false
                                        }
                                        val newName = "${it.name}_redirected_${getRandomString(10)}"
                                        val newMethod = it.genMethod(newName, shouldOuter)
                                        if (newMethod != null) {
                                            it.name = newName
                                            if (it.opcode == Opcodes.INVOKEVIRTUAL) {
                                                it.desc = "(L${it.owner};${it.desc.removePrefix("(")}"
                                                it.opcode = Opcodes.INVOKESTATIC
                                            }

                                            if (NativeCandidateTransformer.enabled) {
                                                if (newMethod.desc.substringAfterLast(")").startsWith("V")) {
                                                    if (nativeDownCall) {
                                                        newMethod.visitAnnotation(
                                                            NativeCandidateTransformer.nativeAnnotation,
                                                            false
                                                        )
                                                        NativeCandidateTransformer.appendedMethods.add(newMethod)
                                                    }
                                                } else if (nativeUpCall) {
                                                    newMethod.visitAnnotation(
                                                        NativeCandidateTransformer.nativeAnnotation,
                                                        false
                                                    )
                                                    NativeCandidateTransformer.appendedMethods.add(newMethod)
                                                }
                                            }

                                            if (shouldOuter) {
                                                val newOwner = newClasses.getOrPut(classNode) {
                                                    ClassNode().apply {
                                                        visit(
                                                            classNode.version,
                                                            Opcodes.ACC_PUBLIC,
                                                            "${classNode.name}\$MethodStatic",
                                                            null,
                                                            "java/lang/Object",
                                                            null
                                                        )
                                                    }
                                                }
                                                // Duplicate methods
                                                newOwner.methods.add(newMethod)
                                                if (randomCall) classNode.methods.add(newMethod)
                                                // Random call
                                                it.owner = if (Random.nextBoolean() || randomCall) newOwner.name
                                                else classNode.name
                                            } else {
                                                it.owner = classNode.name
                                                classNode.methods.add(newMethod)
                                            }
                                            add(1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        }.get()
        newClasses.forEach { (_, new) -> addTrashClass(new) }
        Logger.info("    Redirected $count method calls")
        if (generateOuterClass) Logger.info("    Generated ${newClasses.size} outer classes")
    }

    private fun MethodInsnNode.genMethod(methodName: String, outer: Boolean): MethodNode? {
        return when (opcode) {
            Opcodes.INVOKESTATIC -> method(
                (if (outer) Opcodes.ACC_PUBLIC else Opcodes.ACC_PRIVATE) + Opcodes.ACC_STATIC,
                methodName,
                desc,
                null,
                null
            ) {
                InsnList {
                    var stack = 0
                    Type.getArgumentTypes(methodNode.desc).forEach {
                        VAR(it.getLoadType(), stack)
                        stack += it.size
                    }
                    INVOKESTATIC(owner, name, desc)
                    INSN(methodNode.desc.getReturnType())
                }
            }

            Opcodes.INVOKEVIRTUAL -> method(
                (if (outer) Opcodes.ACC_PUBLIC else Opcodes.ACC_PRIVATE) + Opcodes.ACC_STATIC,
                methodName,
                "(L$owner;${desc.removePrefix("(")}",
                null,
                null
            ) {
                InsnList {
                    var stack = 0
                    Type.getArgumentTypes(methodNode.desc).forEach {
                        VAR(it.getLoadType(), stack)
                        stack += it.size
                    }
                    INVOKEVIRTUAL(owner, name, desc)
                    INSN(methodNode.desc.getReturnType())
                }
            }

            // TODO InvokeInterface
            Opcodes.INVOKEINTERFACE -> null

            // See InitializerRedirect
            Opcodes.INVOKESPECIAL -> null
            else -> throw Exception("Unsupported")
        }
    }

}