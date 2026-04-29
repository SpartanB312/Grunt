package net.spartanb312.grunteon.obfuscator.process.transformers.redirect

import kotlinx.serialization.Serializable

import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.*
import net.spartanb312.grunteon.obfuscator.util.collection.FastObjectArrayList
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.*
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.isExcluded
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

@Transformer.Description(
    "process.redirect.field_access_proxy.desc",
    "Redirect get/put field operations to getter/setter"
)
class FieldAccessProxy : Transformer<FieldAccessProxy.Config>(
    "FieldAccessProxy",
    Category.Redirect,
) {
    @Serializable
    data class Config(
        @SettingDesc(enText = "Specify class include/exclude rules")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc(enText = "The chance that attempt to replace put/set to getter/setter")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        val chance: Double = 1.0,
        @SettingDesc(enText = "Replace GETSTATIC to static getter")
        val getStatic: Boolean = true,
        @SettingDesc(enText = "Replace PUTSTATIC to static setter")
        val putStatic: Boolean = true,
        @SettingDesc(enText = "Replace GETFIELD to getter")
        val getField: Boolean = true,
        @SettingDesc(enText = "Replace PUTFIELD to setter")
        val putField: Boolean = true,
        @SettingDesc(enText = "Generate an outer class to store proxies")
        val outer: Boolean = true,
        @SettingDesc(enText = "Specify method exclusions.")
        val exclusion: List<String> = listOf(
            "net/dummy/**",
            "net/dummy/Class",
            "net/dummy/Class.method",
            "net/dummy/Class.method()V"
        )
    ) : TransformerConfig()

    private lateinit var methodExPredicate: NamePredicates

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        data class ProxyMethod(val sourceNode: ClassNode, val ownerName: String, val method: MethodNode)

        barrier()
        pre {
            //Logger.info(" > FieldAccessProxy: Redirecting field calls...")
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        // Owner class name to method
        val genMethods = reducibleScopeValue {
            MergeableObjectList<ProxyMethod>(FastObjectArrayList())
        }

        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            val counter = counter.local
            val genMethods = genMethods.local
            if (classNode.isExcluded(DISABLE_FIELD_PROXY)) return@parForEachClassesFiltered
            classNode.methods.toList().asSequence()
                .filter { !it.isAbstract && !it.isNative && !it.isInitializer }
                .forEach { method ->
                    if (method.isExcluded(DISABLE_FIELD_PROXY)) return@forEach
                    val excluded = methodExPredicate.matchedAnyBy(methodFullDesc(classNode, method))
                    if (excluded) return@forEach
                    val randomGen = Xoshiro256PPRandom(getSeed(classNode.name, method.name, method.desc))
                    method.instructions.toList().forEach { instruction ->
                        if (instruction !is FieldInsnNode) return@forEach
                        if (randomGen.nextFloat() > config.chance) return@forEach
                        val callingOwner = instance.workRes.getClassNode(instruction.owner) ?: return@forEach
                        if (callingOwner.isExcluded(IGNORE_FIELD_PROXY)) return@forEach
                        val callingField = callingOwner.fields?.toList()?.find {
                            it.name == instruction.name && it.desc == instruction.desc
                        } ?: return@forEach
                        if (callingField.isExcluded(IGNORE_FIELD_PROXY)) return@forEach
                        // Generate proxy
                        val extractToOuterClass = config.outer && callingOwner.isPublic && callingField.isPublic
                        val genMethod = when {
                            instruction.opcode == Opcodes.GETSTATIC && config.getStatic ->
                                genMethod(
                                    instruction,
                                    "get_${instruction.name}_${randomGen.getRandomString(5)}",
                                    callingField.signature
                                ).appendAnnotation(GENERATED_METHOD)

                            instruction.opcode == Opcodes.PUTSTATIC && config.putStatic ->
                                genMethod(
                                    instruction,
                                    "set_${instruction.name}_${randomGen.getRandomString(5)}",
                                    callingField.signature
                                ).appendAnnotation(GENERATED_METHOD)

                            instruction.opcode == Opcodes.GETFIELD && config.getField ->
                                genMethod(
                                    instruction,
                                    "get_${instruction.name}_${randomGen.getRandomString(5)}",
                                    callingField.signature
                                ).appendAnnotation(GENERATED_METHOD)

                            instruction.opcode == Opcodes.PUTFIELD && config.putField ->
                                genMethod(
                                    instruction,
                                    "set_${instruction.name}_${randomGen.getRandomString(5)}",
                                    callingField.signature
                                ).appendAnnotation(GENERATED_METHOD)

                            else -> null
                        }

                        if (genMethod == null) return@forEach

                        val genMethodOwnerName: String
                        if (extractToOuterClass) {
                            genMethod.access = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC
                            genMethodOwnerName = "${classNode.name}${GENERATED_CLASS_SUFFIX}"
                        } else {
                            genMethodOwnerName = classNode.name
                        }

                        method.instructions.set(
                            instruction,
                            MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                genMethodOwnerName,
                                genMethod.name,
                                genMethod.desc
                            )
                        )

                        genMethods.add(ProxyMethod(classNode, genMethodOwnerName, genMethod))

                        counter.add()
                    }
                }
        }
        val outerClassCounter = globalScopeValue { MergeableCounter() }
        seq {
            val outerClassCounter = outerClassCounter.global
            genMethods.global.asSequence()
                .groupBy { it.ownerName }
                .forEach { (targetClass, methods) ->
                    val dstClass: ClassNode
                    if (targetClass.endsWith(GENERATED_CLASS_SUFFIX)) {
                        dstClass = ClassNode()
                        dstClass.visit(
                            methods.first().sourceNode.version,
                            Opcodes.ACC_PUBLIC,
                            targetClass,
                            null,
                            "java/lang/Object",
                            null
                        )
                        dstClass.appendAnnotation(GENERATED_CLASS)
                        instance.workRes.addGeneratedClass(dstClass)
                        outerClassCounter.add()
                    } else {
                        dstClass = instance.workRes.getClassNode(targetClass)
                            ?: error("Target class $targetClass not found, this shouldn't happen")
                    }
                    methods.mapTo(dstClass.methods) { it.method }
                }
        }
        post {
            Logger.info(" - FieldAccessProxy:")
            if (config.outer) Logger.info("    Generated ${outerClassCounter.global.get()} outer classes")
            Logger.info("    Redirected ${counter.global.get()} field calls")
        }
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

    companion object {
        private const val GENERATED_CLASS_SUFFIX = "\$FieldProxy"
    }
}
