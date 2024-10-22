package net.spartanb312.grunt.process.transformers.redirect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.spartanb312.genesis.kotlin.extensions.PRIVATE
import net.spartanb312.genesis.kotlin.extensions.PUBLIC
import net.spartanb312.genesis.kotlin.extensions.STATIC
import net.spartanb312.genesis.kotlin.extensions.insn.INVOKESTATIC
import net.spartanb312.genesis.kotlin.extensions.insn.INVOKEVIRTUAL
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunt.annotation.DISABLE_SCRAMBLE
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.misc.NativeCandidateTransformer
import net.spartanb312.grunt.utils.*
import net.spartanb312.grunt.utils.extensions.getCallingMethodNodeAndOwner
import net.spartanb312.grunt.utils.extensions.hasAnnotation
import net.spartanb312.grunt.utils.extensions.isPrivate
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import kotlin.random.Random

/**
 * Redirect method calls
 * Last update on 24/10/12
 */
object MethodScrambleTransformer : Transformer("MethodScramble", Category.Redirect) {

    private val generateOuterClass by setting("GenerateOuterClass", false)
    private val randomCall by setting("RandomCall", true)
    private val excludedClasses by setting("ExcludedClasses", listOf())
    private val excludedMethodName by setting("ExcludedMethodName", listOf())

    private val nativeDownCall by setting("NativeDownCall", false)
    private val nativeUpCall by setting("NativeUpCall", false)

    override fun ResourceCache.transform() {
        Logger.info(" - Redirecting method calls...")
        val newClasses = mutableMapOf<ClassNode, ClassNode>() // Owner Companion
        val count = count {
            runBlocking {
                nonExcluded.asSequence()
                    .filter { it.name.notInList(excludedClasses) }
                    .forEach { classNode ->
                        fun job() {
                            classNode.methods.toList().forEach { methodNode ->
                                methodNode.instructions.toList().forEach {
                                    if (it is MethodInsnNode && it.name.notInList(excludedMethodName)) {
                                        val pair = it.getCallingMethodNodeAndOwner(
                                            this@transform,
                                            Configs.Settings.parallel
                                        )
                                        if (pair != null) {
                                            val callingOwner = pair.first
                                            val callingMethod = pair.second
                                            val skipOwner = callingOwner.hasAnnotation(DISABLE_SCRAMBLE)
                                            val skipMethod = callingMethod.hasAnnotation(DISABLE_SCRAMBLE)
                                            if (nonExcluded.contains(callingOwner) && !skipOwner && !skipMethod) {
                                                var shouldOuter = generateOuterClass
                                                // Set accesses
                                                if (shouldOuter) {
                                                    if (callingMethod.isPrivate || callingMethod.isPrivate) shouldOuter =
                                                        false
                                                }
                                                val newName = "${it.name}_redirected_${getRandomString(10)}"
                                                val newMethod = it.genMethod(
                                                    newName,
                                                    callingMethod.signature,
                                                    callingMethod.exceptions.toTypedArray(),
                                                    shouldOuter
                                                )
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
                                                        val newOwner = synchronized(this@transform) {
                                                            newClasses.getOrPut(classNode) {
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
                        if (Configs.Settings.parallel) launch(Dispatchers.Default) { job() } else job()
                    }
            }
        }.get()
        newClasses.forEach { (_, new) -> addClass(new) }
        Logger.info("    Redirected $count method calls")
        if (generateOuterClass) Logger.info("    Generated ${newClasses.size} outer classes")
    }

    private fun MethodInsnNode.genMethod(
        methodName: String,
        signature: String?,
        exceptions: Array<String>?,
        outer: Boolean
    ): MethodNode? {
        return when (opcode) {
            Opcodes.INVOKESTATIC -> method(
                (if (outer) PUBLIC else PRIVATE) + STATIC,
                methodName,
                desc,
                signature,
                exceptions
            ) {
                INSTRUCTIONS {
                    var stack = 0
                    Type.getArgumentTypes(methodNode.desc).forEach {
                        +VarInsnNode(it.getLoadType(), stack)
                        stack += it.size
                    }
                    INVOKESTATIC(owner, name, desc)
                    +InsnNode(methodNode.desc.getReturnType())
                }
            }

            Opcodes.INVOKEVIRTUAL -> method(
                (if (outer) PUBLIC else PRIVATE) + STATIC,
                methodName,
                "(L$owner;${desc.removePrefix("(")}",
                signature,
                exceptions
            ) {
                INSTRUCTIONS {
                    var stack = 0
                    Type.getArgumentTypes(methodNode.desc).forEach {
                        +VarInsnNode(it.getLoadType(), stack)
                        stack += it.size
                    }
                    INVOKEVIRTUAL(owner, name, desc)
                    +InsnNode(methodNode.desc.getReturnType())
                }
            }

            Opcodes.INVOKEINTERFACE -> null
            Opcodes.INVOKESPECIAL -> null
            else -> throw Exception("Unsupported")
        }
    }

}