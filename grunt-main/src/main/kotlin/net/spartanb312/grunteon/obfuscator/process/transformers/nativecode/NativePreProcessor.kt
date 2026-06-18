package net.spartanb312.grunteon.obfuscator.process.transformers.nativecode

import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.*
import net.spartanb312.grunteon.obfuscator.util.extensions.*
import net.spartanb312.grunteon.obfuscator.util.filters.withMapping
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

@Transformer.CreditMultiplier(1.2)
@Transformer.Stability(StableLevel.Developing)
@Transformer.Description(
    "process.native.native_pre_processor.desc",
    "Bridge JVM dynamic constants and invokedynamic call sites through same-class helper methods before native codegen"
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
        @SettingDesc("Prefix for generated constant dynamic bridge helper methods")
        @SettingName("Condy helper prefix")
        val constantDynamicHelperPrefix: String = "__grt\$condy\$",
        @SettingDesc("Prefix for generated MethodHandle.invoke bridge helper methods")
        @SettingName("MethodHandle helper prefix")
        val methodHandleHelperPrefix: String = "__grt\$mh\$",
        @SettingDesc("Bridge MethodHandle.invoke/invokeExact calls through excluded same-class helper methods")
        @SettingName("Bridge MethodHandle invokes")
        val bridgeMethodHandleInvokes: Boolean = true,
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
        val condyCounter = reducibleScopeValue { MergeableCounter() }
        val methodHandleCounter = reducibleScopeValue { MergeableCounter() }
        val helperCounter = reducibleScopeValue { MergeableCounter() }

        parForEachClassesFiltered(
            instance.globalExclusion
                .and(instance.mixinExclusion)
                .and(config.classFilter.toClassPredicate())
                .withMapping(instance.nameMapping.revMappings)
        ) { classNode ->
            val result = bridgeInvokeDynamics(classNode, config)
            if (result.indyCount != 0 || result.constantDynamicCount != 0 || result.methodHandleInvokeCount != 0) {
                indyCounter.local.add(result.indyCount)
                condyCounter.local.add(result.constantDynamicCount)
                methodHandleCounter.local.add(result.methodHandleInvokeCount)
                helperCounter.local.add(result.helperCount)
            }
        }

        post {
            Logger.info(" - NativePreProcessor:")
            credit.add((indyCounter.global.get() + condyCounter.global.get() + methodHandleCounter.global.get()) * 50L)
            Logger.info("    Bridged ${indyCounter.global.get()} invokedynamic call sites")
            Logger.info("    Bridged ${condyCounter.global.get()} constant dynamic values")
            Logger.info("    Bridged ${methodHandleCounter.global.get()} MethodHandle.invoke call sites")
            Logger.info("    Generated ${helperCounter.global.get()} same-class helper methods")
        }
    }

    internal fun bridgeInvokeDynamics(classNode: ClassNode, config: Config): BridgeResult {
        val originalMethods = classNode.methods.toList()
        val existingNames = classNode.methods.mapTo(mutableSetOf()) { it.name }
        val helpers = mutableListOf<MethodNode>()
        var indyCount = 0
        var constantDynamicCount = 0
        var methodHandleInvokeCount = 0

        for (method in originalMethods) {
            if (!method.shouldProcess(config)) continue

            val instructions = method.instructions ?: continue
            val dynamicNodes = instructions.toArray().filter {
                it is InvokeDynamicInsnNode ||
                    it.constantDynamicOrNull() != null ||
                    (config.bridgeMethodHandleInvokes && it.isMethodHandleInvoke())
            }
            if (dynamicNodes.isEmpty()) continue

            var methodIndyIndex = 0
            var methodConstantDynamicIndex = 0
            for (dynamicNode in dynamicNodes) {
                when (dynamicNode) {
                    is InvokeDynamicInsnNode -> {
                        val helperName = nextHelperName(config.helperPrefix, method.name, methodIndyIndex++, existingNames)
                        val helper = createIndyHelper(classNode, helperName, dynamicNode)
                        helpers += helper

                        instructions.insertBefore(
                            dynamicNode,
                            MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                classNode.name,
                                helper.name,
                                helper.desc,
                                classNode.isInterface
                            )
                        )
                        instructions.remove(dynamicNode)
                        indyCount++
                    }
                    is LdcInsnNode -> {
                        val constantDynamic = dynamicNode.constantDynamicOrNull()
                        if (constantDynamic != null) {
                            val helperName = nextHelperName(
                                config.constantDynamicHelperPrefix,
                                method.name,
                                methodConstantDynamicIndex++,
                                existingNames
                            )
                            val helper = createConstantDynamicHelper(classNode, helperName, constantDynamic)
                            helpers += helper

                            instructions.insertBefore(
                                dynamicNode,
                                MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    classNode.name,
                                    helper.name,
                                    helper.desc,
                                    classNode.isInterface
                                )
                            )
                            instructions.remove(dynamicNode)
                            constantDynamicCount++
                        }
                    }
                    is MethodInsnNode -> {
                        if (dynamicNode.isMethodHandleInvoke()) {
                            val helperName = nextHelperName(
                                config.methodHandleHelperPrefix,
                                method.name,
                                methodHandleInvokeCount,
                                existingNames
                            )
                            val helper = createMethodHandleInvokeHelper(classNode, helperName, dynamicNode)
                            helpers += helper

                            instructions.insertBefore(
                                dynamicNode,
                                MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    classNode.name,
                                    helper.name,
                                    helper.desc,
                                    classNode.isInterface
                                )
                            )
                            instructions.remove(dynamicNode)
                            methodHandleInvokeCount++
                        }
                    }
                    else -> Unit
                }
            }
        }

        if (helpers.isNotEmpty()) {
            classNode.methods.addAll(helpers)
        }
        return BridgeResult(indyCount, constantDynamicCount, methodHandleInvokeCount, helpers.size)
    }

    private fun AbstractInsnNode.constantDynamicOrNull(): ConstantDynamic? {
        return (this as? LdcInsnNode)?.cst as? ConstantDynamic
    }

    private fun AbstractInsnNode.isMethodHandleInvoke(): Boolean {
        val method = this as? MethodInsnNode ?: return false
        return method.opcode == Opcodes.INVOKEVIRTUAL &&
            method.owner == "java/lang/invoke/MethodHandle" &&
            (method.name == "invoke" || method.name == "invokeExact")
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
        helper.appendNativeBridgeAnnotations()
        return helper
    }

    private fun createMethodHandleInvokeHelper(
        classNode: ClassNode,
        helperName: String,
        invoke: MethodInsnNode
    ): MethodNode {
        val argTypes = Type.getArgumentTypes(invoke.desc)
        val returnType = Type.getReturnType(invoke.desc)
        val helperDesc = Type.getMethodDescriptor(
            returnType,
            Type.getObjectType("java/lang/invoke/MethodHandle"),
            *argTypes
        )
        val helper = MethodNode(
            Opcodes.ASM9,
            helperAccess(classNode),
            helperName,
            helperDesc,
            null,
            null
        )

        helper.instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
        var local = 1
        for (argType in argTypes) {
            helper.instructions.add(VarInsnNode(argType.getOpcode(Opcodes.ILOAD), local))
            local += argType.size
        }
        helper.instructions.add(
            MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/invoke/MethodHandle",
                invoke.name,
                invoke.desc,
                false
            )
        )
        helper.instructions.add(InsnNode(returnType.getOpcode(Opcodes.IRETURN)))
        helper.maxLocals = local
        helper.maxStack = maxOf(1 + argTypes.sumOf { it.size }, returnType.size)
        helper.appendNativeBridgeAnnotations()
        return helper
    }

    private fun createConstantDynamicHelper(
        classNode: ClassNode,
        helperName: String,
        constantDynamic: ConstantDynamic
    ): MethodNode {
        val returnType = Type.getType(constantDynamic.descriptor)
        val helper = MethodNode(
            Opcodes.ASM9,
            helperAccess(classNode),
            helperName,
            Type.getMethodDescriptor(returnType),
            null,
            null
        )

        helper.instructions.add(
            LdcInsnNode(
                ConstantDynamic(
                    constantDynamic.name,
                    constantDynamic.descriptor,
                    constantDynamic.bootstrapMethod,
                    *copyBootstrapArgs(
                        Array(constantDynamic.bootstrapMethodArgumentCount) {
                            constantDynamic.getBootstrapMethodArgument(it)
                        }
                    )
                )
            )
        )
        helper.instructions.add(InsnNode(returnType.getOpcode(Opcodes.IRETURN)))
        helper.maxLocals = 0
        helper.maxStack = returnType.size
        helper.appendNativeBridgeAnnotations()
        return helper
    }

    private fun MethodNode.appendNativeBridgeAnnotations() {
        appendAnnotation(GENERATED_METHOD)
        appendAnnotation(NATIVE_JVM_BRIDGE)
        appendAnnotation(NATIVE_EXCLUDED)
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

    internal data class BridgeResult(
        val indyCount: Int,
        val constantDynamicCount: Int,
        val methodHandleInvokeCount: Int,
        val helperCount: Int
    )
}
