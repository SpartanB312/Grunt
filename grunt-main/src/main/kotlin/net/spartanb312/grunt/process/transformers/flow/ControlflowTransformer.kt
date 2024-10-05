package net.spartanb312.grunt.process.transformers.flow

import net.spartanb312.genesis.instructions
import net.spartanb312.grunt.annotation.DISABLE_CONTROLFLOW
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.MethodProcessor
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.hierarchy.Hierarchy
import net.spartanb312.grunt.process.hierarchy.ReferenceSearch
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.flow.process.JunkCode
import net.spartanb312.grunt.process.transformers.flow.process.ReplaceGoto
import net.spartanb312.grunt.process.transformers.flow.process.ReplaceIf
import net.spartanb312.grunt.process.transformers.flow.process.TableSwitch
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.hasAnnotation
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.notInList
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.MethodNode
import kotlin.random.Random

/**
 * Obfuscating the controlflow
 * Last update on 24/09/24
 */
object ControlflowTransformer : Transformer("Controlflow", Category.Controlflow), MethodProcessor {

    private val intensity by setting("Intensity", 1)
    private var beforeEncrypt by setting("ExecuteBeforeEncrypt", false)
    private val bogusJump by setting("BogusConditionJump", true)
    private val mangledIf by setting("MangledCompareJump", true)
    private val tableSwitch by setting("TableSwitchJump", true)
    private val switchRate by setting("SwitchReplaceRate", 30)
    private val maxSwitchCase by setting("MaxSwitchCase", 5)
    val reverseExistedIf by setting("ReverseExistedIf", true)
    val reverseChance by setting("ReverseChance", 50)
    val trappedCase by setting("TrappedSwitchCase", true)
    val trapChance by setting("TrapChance", 30)
    val fakeLoop by setting("UnconditionalLoop", true)
    val loopChance by setting("LoopChance", 30)
    val useLocalVar by setting("UseLocalVar", true)
    val junkCode by setting("JunkCode", true)
    val maxJunkCode by setting("MaxJunkCode", 3)
    val expandedJunkCode by setting("ExpandedJunkCode", true)
    val exclusion by setting("Exclusion", listOf())

    override var order: Int
        get() = if (beforeEncrypt) 200 else 400
        set(value) {
            beforeEncrypt = value == 200
        }

    override fun ResourceCache.transform() {
        Logger.info(" - Replacing jumps to implicit operations")
        JunkCode.refresh(this)
        val hierarchy = Hierarchy(this)
        hierarchy.build(true)
        val count = count {
            nonExcluded.asSequence()
                .filter {
                    it.name.notInList(exclusion)
                            && !it.missingReference(hierarchy)
                            && !it.hasAnnotation(DISABLE_CONTROLFLOW)
                }.forEach { classNode ->
                    classNode.methods.forEach { methodNode ->
                        if (!methodNode.hasAnnotation(DISABLE_CONTROLFLOW)) {
                            add(processMethodNode(methodNode))
                        }
                    }
                }
        }
        Logger.info("    Replaced ${count.get()} jumps")
    }

    override fun transformMethod(owner: ClassNode, method: MethodNode) {
        processMethodNode(method)
    }

    private fun processMethodNode(methodNode: MethodNode): Int {
        var count = 0
        repeat(intensity) {
            if (bogusJump) {
                val newInsn = instructions {
                    val returnType = Type.getReturnType(methodNode.desc)
                    methodNode.instructions.forEach { insnNode ->
                        if (insnNode is JumpInsnNode && insnNode.opcode == Opcodes.GOTO) {
                            +ReplaceGoto.generate(
                                insnNode.label,
                                methodNode,
                                returnType,
                                Random.nextBoolean()
                            )
                            count++
                        } else +insnNode
                    }
                }
                methodNode.instructions = newInsn
            }
            if (mangledIf) {
                val newInsn = instructions {
                    val returnType = Type.getReturnType(methodNode.desc)
                    methodNode.instructions.forEach { insnNode ->
                        if (insnNode is JumpInsnNode && ReplaceIf.ifComparePairs.any { it.key == insnNode.opcode }) {
                            +ReplaceIf.generate(
                                insnNode,
                                insnNode.label,
                                methodNode,
                                returnType,
                                Random.nextBoolean()
                            )
                            count++
                        } else +insnNode
                    }
                }
                methodNode.instructions = newInsn
            }
            if (tableSwitch) {
                val newInsn = instructions {
                    val range = 1..maxSwitchCase.coerceAtLeast(1)
                    val returnType = Type.getReturnType(methodNode.desc)
                    methodNode.instructions.forEach { insnNode ->
                        if (insnNode is JumpInsnNode && insnNode.opcode == Opcodes.GOTO
                            && Random.nextInt(0, 100) < switchRate
                        ) {
                            +TableSwitch.generate(
                                insnNode.label,
                                methodNode,
                                returnType,
                                range.random(),
                                Random.nextBoolean()
                            )
                            count++
                        } else +insnNode
                    }
                }
                methodNode.instructions = newInsn
            }
        }
        if ((mangledIf || bogusJump) && useLocalVar) methodNode.maxLocals += 1
        return count
    }


    private fun ClassNode.missingReference(hierarchy: Hierarchy): Boolean {
        return ReferenceSearch.checkMissing(this, hierarchy).isNotEmpty()
    }

}