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
import net.spartanb312.grunteon.obfuscator.util.extensions.isAnnotation
import net.spartanb312.grunteon.obfuscator.util.extensions.isInterface
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.tree.*
import java.lang.reflect.Modifier

/**
 * Last update on 2026/03/31 by FluixCarvin
 * @author jonesdevelopment
 */
@Transformer.CreditMultiplier(1.0)
@Transformer.Stability(StableLevel.Stable)
@Transformer.Description(
    "process.miscellaneous.declared_fields_extract.desc",
    "Extract static field initialization to <clinit>"
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
        parForEachClassesFiltered(
            instance.globalExclusion
                .and(instance.mixinExclusion)
                .and(config.classFilter.toClassPredicate())
        ) { classNode ->
            if (classNode.isAnnotation) return@parForEachClassesFiltered
            if (classNode.isInterface) return@parForEachClassesFiltered // compile-time constant won't invoke <clinit>
            val counter = counter.local
            var clinit = classNode.methods.firstOrNull { it.name.equals("<clinit>") }
            for (field in classNode.fields) {
                // ConstantValue is ignored by the JVM for instance fields (JVMS 4.7.2).
                if (field.value != null && Modifier.isStatic(field.access)) {
                    if (clinit == null) {
                        clinit = clinit()
                        clinit.instructions.add(InsnNode(RETURN))
                        classNode.methods.add(clinit)
                    }
                    clinit.instructions.insert(box(classNode, field))
                    field.value = null
                    counter.add()
                }
            }
        }
        post {
            Logger.info(" - DeclaredFieldsExtract:")
            credit.add(counter.global.get() * 150L)
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
            PUTSTATIC(owner.name, field.name, field.desc)
        }
    }

}
