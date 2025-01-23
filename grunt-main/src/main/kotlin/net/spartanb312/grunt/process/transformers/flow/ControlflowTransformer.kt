package net.spartanb312.grunt.process.transformers.flow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.grunt.annotation.DISABLE_CONTROLFLOW
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.MethodProcessor
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.hierarchy.Hierarchy
import net.spartanb312.grunt.process.hierarchy.ReferenceSearch
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.flow.process.*
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.hasAnnotation
import net.spartanb312.grunt.utils.extensions.isDummy
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.notInList
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import kotlin.random.Random

/**
 * Obfuscating the controlflow
 * Last update on 24/10/22
 */
object ControlflowTransformer : Transformer("Controlflow", Category.Controlflow), MethodProcessor {

    private val intensity by setting("Intensity", 1)  // Range 1..3
    private var beforeEncrypt by setting("ExecuteBeforeEncrypt", false)
    private val switchExtractor by setting("SwitchExtractor", true) // Author: jonesdevelopment
    private val bogusJump by setting("BogusConditionJump", true)
    private val gotoRate by setting("GotoReplaceRate", 80) // Range 0..100
    private val mangledIf by setting("MangledCompareJump", true)
    private val ifRate by setting("IfReplaceRate", 50) // Range 0..100
    private val ifCompareRate by setting("IfICompareReplaceRate", 100) // Range 0..100
    private val switchProtect by setting("SwitchProtect", true)
    private val tableSwitch by setting("TableSwitchJump", true)
    private val switchRate by setting("SwitchReplaceRate", 30)  // Range 0..100
    private val maxSwitchCase by setting("MaxSwitchCase", 5) // Range 1..10
    val reverseExistedIf by setting("ReverseExistedIf", true)
    val reverseChance by setting("ReverseChance", 50) // Range 0..100
    val trappedCase by setting("TrappedSwitchCase", true)
    val trapChance by setting("TrapChance", 50) // Range 0..100
    var arithmeticExpr by setting("ArithmeticExprBuilder", true)
    val asIntensity by setting("BuilderIntensity", 1) // Range 1..3
    val junkParameter by setting("JunkBuilderParameter", true)
    val annotationOnBuilder by setting("BuilderNativeAnnotation", false)
    val useLocalVar by setting("UseLocalVar", true)
    val junkCode by setting("JunkCode", true)
    val maxJunkCode by setting("MaxJunkCode", 2)  // Range 1..5
    val expandedJunkCode by setting("ExpandedJunkCode", true)
    val exclusion by setting("Exclusion", listOf())

    override var order: Int
        get() = if (beforeEncrypt) 200 else 400
        set(value) {
            beforeEncrypt = value == 200
        }

    override fun ResourceCache.transform() {
        Logger.info(" - Transforming controlflows...")
        JunkCode.refresh(this)
        ArithmeticExpr.refresh(this)
        val hierarchy = Hierarchy(this)
        hierarchy.build(true)
        val count = count {
            runBlocking {
                nonExcluded.asSequence()
                    .filter {
                        it.name.notInList(exclusion)
                                && !it.missingReference(hierarchy)
                                && !it.hasAnnotation(DISABLE_CONTROLFLOW)
                    }.forEach { classNode ->
                        fun job() {
                            classNode.methods.toList().forEach { methodNode ->
                                if (!methodNode.hasAnnotation(DISABLE_CONTROLFLOW)) {
                                    add(processMethodNode(classNode, methodNode, false))
                                }
                            }
                        }
                        if (Configs.Settings.parallel) launch(Dispatchers.Default) { job() } else job()
                    }
            }
        }
        Logger.info("    Replaced ${count.get()} jumps")
    }

    fun transformMethod(owner: ClassNode, method: MethodNode, indyReobf: Boolean) {
        processMethodNode(owner, method, indyReobf)
    }

    override fun transformMethod(owner: ClassNode, method: MethodNode) {
        processMethodNode(owner, method, false)
    }

    private fun processMethodNode(owner: ClassNode, methodNode: MethodNode, indyReobf: Boolean): Int {
        var count = 0
        repeat(intensity) {
            if (switchProtect) {
                val newInsn = instructions { // step1: replace switches { switch: m }
                    methodNode.instructions.forEach { insnNode ->
                        when (insnNode) {
                            is LookupSwitchInsnNode -> +LookUpSwitch.generate(insnNode)
                            is TableSwitchInsnNode -> +LookUpSwitch.generate(insnNode)
                            else -> +insnNode
                        }
                    }
                }
                methodNode.instructions = newInsn
            }
            if (switchExtractor) { // step2: extract switches { switch: m, if: n2 = n + Σ(0->m) cases }
                val newInsn = instructions {
                    methodNode.instructions.forEach { insnNode ->
                        if (insnNode is TableSwitchInsnNode || insnNode is LookupSwitchInsnNode) {
                            +SwitchExtractor.generate(
                                insnNode,
                                methodNode.maxLocals++
                            )
                            count++
                        } else +insnNode
                    }
                }
                methodNode.instructions = newInsn
            }
            if (bogusJump) { // step3: replace goto { goto: l1 = l - dl, if: n3 = n2 + dl }
                val newInsn = instructions {
                    val returnType = Type.getReturnType(methodNode.desc)
                    methodNode.instructions.forEach { insnNode ->
                        if (insnNode is JumpInsnNode && insnNode.opcode == Opcodes.GOTO && !insnNode.previous.isDummy
                            && Random.nextInt(0, 100) <= gotoRate
                        ) {
                            +ReplaceGoto.generate(
                                insnNode.label,
                                owner,
                                methodNode,
                                returnType,
                                Random.nextBoolean(),
                                indyReobf
                            )
                            count++
                        } else +insnNode
                    }
                }
                methodNode.instructions = newInsn
            }
            if (mangledIf) { // step4: process if opcode { if: n4 ≈ n3 + dn * 2, goto: l2 ≈ l1 + dn * 2 }
                val newInsn = instructions {
                    val returnType = Type.getReturnType(methodNode.desc)
                    methodNode.instructions.forEach { insnNode ->
                        if (insnNode is JumpInsnNode) {
                            val replaceIf = ReplaceIf.ifOpcodes.any { it == insnNode.opcode }
                                    && Random.nextInt(0, 100) <= ifRate
                            val replaceCompare = ReplaceIf.ifCompareOpcodes.any { it == insnNode.opcode }
                                    && Random.nextInt(0, 100) <= ifCompareRate
                            if (replaceIf || replaceCompare) {
                                +ReplaceIf.generate(
                                    insnNode,
                                    insnNode.label,
                                    owner,
                                    methodNode,
                                    returnType,
                                    Random.nextBoolean(),
                                    indyReobf
                                )
                                count++
                            } else +insnNode
                        } else +insnNode
                    }
                }
                methodNode.instructions = newInsn
            }
            if (tableSwitch) { // step5: replace goto to switch { switch: m2 = m + E(case) * l2 * rate }
                val newInsn = instructions {
                    val range = 1..maxSwitchCase.coerceAtLeast(1)
                    val returnType = Type.getReturnType(methodNode.desc)
                    methodNode.instructions.forEach { insnNode ->
                        if (insnNode is JumpInsnNode && insnNode.opcode == Opcodes.GOTO && !insnNode.previous.isDummy
                            && Random.nextInt(0, 100) <= switchRate
                        ) {
                            +TableSwitch.generate(
                                insnNode.label,
                                owner,
                                methodNode,
                                returnType,
                                range.random(),
                                Random.nextBoolean(),
                                indyReobf
                            )
                            count++
                        } else +insnNode
                    }
                }
                methodNode.instructions = newInsn
            }
        }
        if (tableSwitch) methodNode.maxLocals += 5
        else if ((mangledIf || bogusJump) && useLocalVar) methodNode.maxLocals += 1
        return count
    }

    private fun ClassNode.missingReference(hierarchy: Hierarchy): Boolean {
        return ReferenceSearch.checkMissing(this, hierarchy).isNotEmpty()
    }

}