package net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous

import kotlinx.serialization.Serializable

import net.spartanb312.genesis.kotlin.clinit
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.extensions.init
import net.spartanb312.grunteon.obfuscator.util.extensions.isAnnotation
import net.spartanb312.grunteon.obfuscator.util.extensions.isInterface
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.tree.*
import java.lang.reflect.Modifier

/**
 * Last update on 2026/03/31 by FluixCarvin
 * @author jonesdevelopment
 */
@Transformer.Stability(StableLevel.Stable)
@Transformer.Description(
    "process.miscellaneous.declared_fields_extract.desc",
    "Extract field initialization to <init> or <clinit>"
)
class DeclaredFieldsExtract : Transformer<DeclaredFieldsExtract.Config>(
    "DeclaredFieldsExtract",
    Category.Miscellaneous,
) {
    @Serializable
    data class Config(
        @SettingName("Class filter")
        val classFilter: ClassFilterConfig = ClassFilterConfig()
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            //Logger.info(" > DeclaredFieldsExtract: Transforming local variables...")
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            if (classNode.isAnnotation) return@parForEachClassesFiltered
            if (classNode.isInterface) return@parForEachClassesFiltered // compile-time constant won't invoke <clinit>
            val counter = counter.local
            var clinit = classNode.methods.firstOrNull { it.name.equals("<clinit>") }
            val inits = classNode.methods.filter { it.name.equals("<init>") }
            val isAboveJava22 = classNode.version >= Opcodes.V22
            for (field in classNode.fields) {
                if (field.value != null) {
                    if (Modifier.isStatic(field.access)) {
                        if (clinit == null) {
                            clinit = clinit()
                            clinit.instructions.add(InsnNode(RETURN))
                            classNode.methods.add(clinit)
                        }
                        clinit.instructions.insert(box(classNode, field))
                        field.value = null
                        counter.add()
                    } else if (isAboveJava22) {
                        if (inits.isEmpty()) {
                            val init = init()
                            init.instructions.add(InsnNode(RETURN))
                            init.instructions.insert(box(classNode, field))
                            classNode.methods.add(init)
                        } else inits.forEach {
                            it.instructions.insert(box(classNode, field))
                        }
                        field.value = null
                        counter.add()
                    }
                }
            }
        }
        post {
            Logger.info(" - DeclaredFieldsExtract:")
            Logger.info("    Hid ${counter.global.get()} declared fields")
        }
    }

    private fun box(owner: ClassNode, field: FieldNode): InsnList {
        return instructions {
            when (field.desc) {
                "I" -> INT(field.value as Int)
                "J" -> LONG(field.value as Long)
                "B" -> BIPUSH(field.value as Int)
                "S" -> SIPUSH(field.value as Int)
                else -> +LdcInsnNode(field.value)
            }
            if (Modifier.isStatic(field.access)) {
                PUTSTATIC(owner.name, field.name, field.desc)
            } else {
                PUTFIELD(owner.name, field.name, field.desc)
            }
        }
    }

}
