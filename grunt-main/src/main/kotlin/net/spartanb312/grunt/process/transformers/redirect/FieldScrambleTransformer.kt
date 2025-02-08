package net.spartanb312.grunt.process.transformers.redirect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.spartanb312.genesis.kotlin.extensions.PUBLIC
import net.spartanb312.genesis.kotlin.extensions.STATIC
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunt.annotation.DISABLE_SCRAMBLE
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.misc.NativeCandidateTransformer
import net.spartanb312.grunt.utils.*
import net.spartanb312.grunt.utils.extensions.hasAnnotation
import net.spartanb312.grunt.utils.extensions.isInitializer
import net.spartanb312.grunt.utils.extensions.isPublic
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

/**
 * Scramble field calls
 * Last update on 2024/10/25
 */
object FieldScrambleTransformer : Transformer("FieldScramble", Category.Redirect) {

    private val intensity by setting("Intensity", 1)
    private val rate by setting("ReplacePercentage", 10)
    private val randomName by setting("RandomName", false)
    private val redirectGetStatic by setting("GetStatic", true)
    private val redirectSetStatic by setting("SetStatic", true)
    private val redirectGetField by setting("GetValue", true)
    private val redirectSetField by setting("SetField", true)
    private val generateOuterClass by setting("GenerateOuterClass", false)
    private val nativeAnnotation by setting("NativeAnnotation", false)
    private val excludedClasses by setting("ExcludedClasses", listOf())
    private val excludedFieldName by setting("ExcludedFieldName", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Redirecting field calls...")
        val newClasses = mutableMapOf<ClassNode, ClassNode>() // Owner Companion
        var count = 0
        repeat(intensity) {
            count += process(newClasses)
        }
        Logger.info("    Redirected $count field calls")
    }

    private fun ResourceCache.process(newClasses: MutableMap<ClassNode, ClassNode>): Int {
        val count = count {
            runBlocking {
                nonExcluded.asSequence()
                    .filter { it.name.notInList(excludedClasses) }
                    .forEach { classNode ->
                        fun job() {
                            classNode.methods.toList().asSequence()
                                .filter { !it.isInitializer }
                                .forEach { methodNode ->
                                    methodNode.instructions.toList().forEach {
                                        if (it is FieldInsnNode && it.name.notInList(excludedFieldName) && (0..99).random() < rate) {
                                            val callingOwner = synchronized(this@process) { getClassNode(it.owner) }
                                            val callingField = callingOwner?.fields?.find { field ->
                                                field.name == it.name && field.desc == it.desc
                                            }
                                            val skipOwner = callingOwner?.hasAnnotation(DISABLE_SCRAMBLE) == true
                                            val skipField = callingField?.hasAnnotation(DISABLE_SCRAMBLE) == true
                                            if (callingField != null && !skipField && !skipOwner) {
                                                val shouldOuter =
                                                    generateOuterClass && callingOwner.isPublic && callingField.isPublic
                                                val genMethod = when {
                                                    it.opcode == Opcodes.GETSTATIC && redirectGetStatic ->
                                                        genMethod(
                                                            it,
                                                            if (randomName) getRandomString(10)
                                                            else "get_${it.name}${getRandomString(5)}",
                                                            callingField.signature
                                                        ).appendAnnotation()

                                                    it.opcode == Opcodes.PUTSTATIC && redirectSetStatic ->
                                                        genMethod(
                                                            it,
                                                            if (randomName) getRandomString(10)
                                                            else "set_${it.name}${getRandomString(5)}",
                                                            callingField.signature
                                                        ).appendAnnotation()

                                                    it.opcode == Opcodes.GETFIELD && redirectGetField ->
                                                        genMethod(
                                                            it,
                                                            if (randomName) getRandomString(10)
                                                            else "get_${it.name}${getRandomString(5)}",
                                                            callingField.signature
                                                        ).appendAnnotation()

                                                    it.opcode == Opcodes.PUTFIELD && redirectSetField ->
                                                        genMethod(
                                                            it,
                                                            if (randomName) getRandomString(10)
                                                            else "set_${it.name}${getRandomString(5)}",
                                                            callingField.signature
                                                        ).appendAnnotation()

                                                    else -> null
                                                }

                                                if (genMethod != null) {
                                                    if (shouldOuter) {
                                                        genMethod.access = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC
                                                        val clazz = synchronized(this@process) {
                                                            newClasses.getOrPut(classNode) {
                                                                ClassNode().apply {
                                                                    visit(
                                                                        classNode.version,
                                                                        Opcodes.ACC_PUBLIC,
                                                                        "${classNode.name}\$FieldStatic",
                                                                        null,
                                                                        "java/lang/Object",
                                                                        null
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        methodNode.instructions.set(
                                                            it,
                                                            MethodInsnNode(
                                                                Opcodes.INVOKESTATIC,
                                                                clazz.name,
                                                                genMethod.name,
                                                                genMethod.desc
                                                            )
                                                        )
                                                        clazz.methods.add(genMethod)
                                                    } else {
                                                        methodNode.instructions.set(
                                                            it,
                                                            MethodInsnNode(
                                                                Opcodes.INVOKESTATIC,
                                                                classNode.name,
                                                                genMethod.name,
                                                                genMethod.desc
                                                            )
                                                        )
                                                        classNode.methods.add(genMethod)
                                                    }
                                                    add()
                                                }
                                            }
                                        }
                                    }
                                }
                        }
                        if (Configs.Settings.parallel) launch(Dispatchers.Default) { job() } else job()
                    }
            }
            newClasses.forEach { (_, c) ->
                classes[c.name] = c
            }
        }.get()
        return count
    }

    private fun MethodNode.appendAnnotation(): MethodNode {
        if (nativeAnnotation) {
            NativeCandidateTransformer.appendedMethods.add(this)
            visitAnnotation(NativeCandidateTransformer.annotation, false)
        }
        return this
    }

    private fun genMethod(field: FieldInsnNode, methodName: String, signature: String?): MethodNode {
        return when (field.opcode) {
            Opcodes.GETFIELD -> method(
                PUBLIC + STATIC,
                methodName,
                "(L${field.owner};)${field.desc}",
                signature
            ) {
                INSTRUCTIONS {
                    ALOAD(0)
                    GETFIELD(field.owner, field.name, field.desc)
                    +InsnNode(methodNode.desc.getReturnType())
                }
            }

            Opcodes.PUTFIELD -> method(
                PUBLIC + STATIC,
                methodName,
                "(L${field.owner};${field.desc})V",
                signature,
            ) {
                INSTRUCTIONS {
                    var stack = 0
                    Type.getArgumentTypes(methodNode.desc).forEach {
                        +VarInsnNode(it.getLoadType(), stack)
                        stack += it.size
                    }
                    PUTFIELD(field.owner, field.name, field.desc)
                    RETURN
                }
            }

            Opcodes.GETSTATIC -> method(
                PUBLIC + STATIC,
                methodName,
                "()${field.desc}",
                signature
            ) {
                INSTRUCTIONS {
                    GETSTATIC(field.owner, field.name, field.desc)
                    +InsnNode(methodNode.desc.getReturnType())
                }
            }

            Opcodes.PUTSTATIC -> method(
                PUBLIC + STATIC,
                methodName,
                "(${field.desc})V",
                signature
            ) {
                INSTRUCTIONS {
                    var stack = 0
                    Type.getArgumentTypes(methodNode.desc).forEach {
                        +VarInsnNode(it.getLoadType(), stack)
                        stack += it.size
                    }
                    PUTSTATIC(field.owner, field.name, field.desc)
                    RETURN
                }
            }

            else -> throw Exception("Unsupported")
        }
    }

}