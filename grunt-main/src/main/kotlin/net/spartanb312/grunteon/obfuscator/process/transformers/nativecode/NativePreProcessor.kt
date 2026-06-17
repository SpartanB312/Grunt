package net.spartanb312.grunteon.obfuscator.process.transformers.nativecode

import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.*
import net.spartanb312.grunteon.obfuscator.util.extensions.*
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

@Transformer.CreditMultiplier(1.2)
@Transformer.Stability(StableLevel.Developing)
@Transformer.Description(
    "process.native.native_pre_processor.desc",
    "Bridge invokedynamic call sites through same-class helper methods before native codegen"
)
class NativePreProcessor : Transformer<NativePreProcessor.Config>(
    "NativePreProcessor",
    Category.Native,
) {

    @Serializable
    data class Config(
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Prefix for generated invokedynamic bridge helper methods")
        @SettingName("Helper prefix")
        val helperPrefix: String = "__grt\$indy\$",
        @SettingDesc("Bridge generated Grunteon helper methods too")
        @SettingName("Process generated methods")
        val processGeneratedMethods: Boolean = true
    ) : TransformerConfig()

    init {
        after(Category.Encryption, "Native preprocessor should run after encryption category")
        after(Category.Controlflow, "Native preprocessor should run after controlflow category")
        after(Category.AntiDebug, "Native preprocessor should run after anti debug category")
        after(Category.Authentication, "Native preprocessor should run after authentication category")
        after(Category.Exploit, "Native preprocessor should run after exploit category")
        after(Category.Miscellaneous, "Native preprocessor should run after miscellaneous category")
        after(Category.Optimization, "Native preprocessor should run after optimization category")
        after(Category.Redirect, "Native preprocessor should run after redirect category")
        after(Category.Renaming, "Native preprocessor should run after renaming category")
        after(Category.Other, "Native preprocessor should run after other category")
        before(Category.PostProcess, "Native preprocessor should run before post process category")
    }

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        val indyCounter = reducibleScopeValue { MergeableCounter() }
        val helperCounter = reducibleScopeValue { MergeableCounter() }

        parForEachClassesFiltered(
            instance.globalExclusion
                .and(instance.mixinExclusion)
                .and(config.classFilter.toClassPredicate())
        ) { classNode ->
            val result = classNode.bridgeInvokeDynamics(config)
            if (result.indyCount != 0) {
                indyCounter.local.add(result.indyCount)
                helperCounter.local.add(result.helperCount)
            }
        }

        post {
            Logger.info(" - NativePreProcessor:")
            credit.add(indyCounter.global.get() * 50L)
            Logger.info("    Bridged ${indyCounter.global.get()} invokedynamic call sites")
            Logger.info("    Generated ${helperCounter.global.get()} same-class helper methods")
        }
    }

    private fun ClassNode.bridgeInvokeDynamics(config: Config): BridgeResult {
        val originalMethods = methods.toList()
        val existingNames = methods.mapTo(mutableSetOf()) { it.name }
        val helpers = mutableListOf<MethodNode>()
        var indyCount = 0

        for (method in originalMethods) {
            if (!method.shouldProcess(config)) continue

            val instructions = method.instructions ?: continue
            val indyNodes = instructions.toArray().filterIsInstance<InvokeDynamicInsnNode>()
            if (indyNodes.isEmpty()) continue

            var methodIndyIndex = 0
            for (indy in indyNodes) {
                val helperName = nextHelperName(config.helperPrefix, method.name, methodIndyIndex++, existingNames)
                val helper = createIndyHelper(this, helperName, indy)
                helpers += helper

                instructions.insertBefore(
                    indy,
                    MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        name,
                        helper.name,
                        helper.desc,
                        isInterface
                    )
                )
                instructions.remove(indy)
                indyCount++
            }
        }

        if (helpers.isNotEmpty()) {
            methods.addAll(helpers)
        }
        return BridgeResult(indyCount, helpers.size)
    }

    private fun MethodNode.shouldProcess(config: Config): Boolean {
        if (isAbstract || isNative) return false
        if (!config.processGeneratedMethods && hasAnnotation(GENERATED_METHOD)) return false
        return true
    }

    private fun createIndyHelper(classNode: ClassNode, helperName: String, indy: InvokeDynamicInsnNode): MethodNode {
        val argTypes = Type.getArgumentTypes(indy.desc)
        val returnType = Type.getReturnType(indy.desc)
        val helper = MethodNode(
            Opcodes.ASM9,
            helperAccess(classNode),
            helperName,
            indy.desc,
            null,
            null
        )

        var local = 0
        for (argType in argTypes) {
            helper.instructions.add(VarInsnNode(argType.getOpcode(Opcodes.ILOAD), local))
            local += argType.size
        }
        helper.instructions.add(
            InvokeDynamicInsnNode(
                indy.name,
                indy.desc,
                indy.bsm,
                *copyBootstrapArgs(indy.bsmArgs)
            )
        )
        helper.instructions.add(InsnNode(returnType.getOpcode(Opcodes.IRETURN)))
        helper.maxLocals = local
        helper.maxStack = maxOf(argTypes.sumOf { it.size }, returnType.size)
        helper.appendAnnotation(GENERATED_METHOD)
        helper.appendAnnotation(NATIVE_JVM_BRIDGE)
        helper.appendAnnotation(NATIVE_EXCLUDED)
        return helper
    }

    private fun helperAccess(classNode: ClassNode): Int {
        val visibility = if (classNode.isInterface && classNode.version < Opcodes.V9) {
            Opcodes.ACC_PUBLIC
        } else {
            Opcodes.ACC_PRIVATE
        }
        return visibility or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC
    }

    private fun copyBootstrapArgs(args: Array<Any?>?): Array<Any?> {
        if (args == null) return emptyArray()
        return args.map { arg ->
            when (arg) {
                is Array<*> -> arg.copyOf()
                else -> arg
            }
        }.toTypedArray()
    }

    private fun nextHelperName(
        prefix: String,
        methodName: String,
        methodIndyIndex: Int,
        existingNames: MutableSet<String>
    ): String {
        val baseName = prefix + sanitizeMethodName(methodName) + "\$" + methodIndyIndex
        var name = baseName
        var collisionIndex = 0
        while (!existingNames.add(name)) {
            name = "$baseName\$$collisionIndex"
            collisionIndex++
        }
        return name
    }

    private fun sanitizeMethodName(name: String): String {
        return when (name) {
            "<init>" -> "init"
            "<clinit>" -> "clinit"
            else -> buildString {
                for (char in name) {
                    append(
                        if (char in 'a'..'z' ||
                            char in 'A'..'Z' ||
                            char in '0'..'9' ||
                            char == '_' ||
                            char == '$'
                        ) {
                            char
                        } else {
                            '_'
                        }
                    )
                }
            }.ifEmpty { "method" }
        }
    }

    private data class BridgeResult(
        val indyCount: Int,
        val helperCount: Int
    )
}
