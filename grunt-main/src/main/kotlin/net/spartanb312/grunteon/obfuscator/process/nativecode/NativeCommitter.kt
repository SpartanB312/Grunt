package net.spartanb312.grunteon.obfuscator.process.nativecode

import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunteon.obfuscator.Grunteon
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode

internal object NativeCommitter {

    context(instance: Grunteon)
    fun commit(bundle: NativeSourceBundle, config: NativePipelineConfig) {
        val plan = bundle.plan
        val loaderProxyMethods = plan.classes
            .flatMap { it.methods }
            .filter { it.commitKind.isLoaderProxy }
            .map { it.registeredName to it.registeredDesc }
        instance.workRes.addGeneratedClass(
            NativeLoaderClassFactory.create(
                loaderInternalName = plan.loaderInternalName,
                libraries = bundle.resolvedLibraryTargets.map {
                    NativeLoaderClassFactory.NativeLoaderLibrary(
                        resourceDirectory = it.platform.resourceDirectory,
                        resourcePath = "/${it.resourceName}",
                        librarySuffix = it.platform.librarySuffix
                    )
                },
                proxyMethods = loaderProxyMethods
            )
        )

        plan.classes.forEach { classPlan ->
            val directClinitProxy = classPlan.methods.firstOrNull {
                it.commitKind == NativeMethodCommitKind.ClassInitializerProxy
            }
            val loaderClinitProxy = classPlan.methods.firstOrNull {
                it.commitKind == NativeMethodCommitKind.InterfaceClassInitializerProxy
            }
            if (directClinitProxy != null) {
                createClinitProxyMethod(classPlan.classNode, directClinitProxy)
                rewriteClassInitializer(classPlan, plan.loaderInternalName, directClinitProxy)
            } else if (loaderClinitProxy != null) {
                rewriteInterfaceClassInitializer(classPlan.classNode, plan.loaderInternalName, loaderClinitProxy)
            } else if (classPlan.methods.any { !it.commitKind.isLoaderProxy }) {
                injectRegistration(classPlan, plan.loaderInternalName)
            }
            classPlan.methods.forEach { binding ->
                when (binding.commitKind) {
                    NativeMethodCommitKind.Direct -> binding.method.methodNode.makeNative()
                    NativeMethodCommitKind.ClassInitializerProxy -> Unit
                    NativeMethodCommitKind.InterfaceClassInitializerProxy -> Unit
                    NativeMethodCommitKind.InterfaceProxy -> rewriteInterfaceMethod(
                        classPlan.classNode,
                        plan.loaderInternalName,
                        binding
                    )
                }
            }
        }

        if (config.cleanNativeAnnotations) {
            cleanNativeAnnotations()
        }
    }

    context(instance: Grunteon)
    fun cleanNativeAnnotations() {
        instance.workRes.inputClassCollection.forEach(NativeAnnotationCleaner::clean)
    }

    private fun MethodNode.makeNative() {
        access = access or Opcodes.ACC_NATIVE
        instructions.clear()
        tryCatchBlocks?.clear()
        localVariables?.clear()
        maxStack = 0
        maxLocals = 0
    }

    private fun createClinitProxyMethod(classNode: ClassNode, binding: NativeMethodBinding) {
        val proxy = method(
            PRIVATE + STATIC + NATIVE + SYNTHETIC,
            binding.registeredName,
            binding.registeredDesc
        ) {
            +AnnotationNode("Ljava/lang/invoke/LambdaForm\$Hidden;")
            +AnnotationNode("Ljdk/internal/vm/annotation/Hidden;")
            MAXS(0, 0)
        }
        classNode.methods.add(proxy)
    }

    private fun rewriteInterfaceClassInitializer(
        classNode: ClassNode,
        loaderInternalName: String,
        binding: NativeMethodBinding
    ) {
        val clinit = binding.method.methodNode
        clinit.access = Opcodes.ACC_STATIC
        clinit.instructions.clear()
        clinit.tryCatchBlocks?.clear()
        clinit.localVariables?.clear()
        clinit.instructions = instructions {
            INVOKESTATIC(loaderInternalName, "load", "()V")
            LDC(Type.getObjectType(classNode.name))
            INVOKESTATIC(loaderInternalName, binding.registeredName, binding.registeredDesc)
            RETURN
        }
        clinit.maxStack = 1
        clinit.maxLocals = 0
    }

    private fun rewriteInterfaceMethod(
        classNode: ClassNode,
        loaderInternalName: String,
        binding: NativeMethodBinding
    ) {
        val method = binding.method.methodNode
        val argumentTypes = Type.getArgumentTypes(method.desc)
        val returnType = Type.getReturnType(method.desc)
        val isStatic = method.access and Opcodes.ACC_STATIC != 0

        method.access = method.access and Opcodes.ACC_NATIVE.inv() and Opcodes.ACC_ABSTRACT.inv()
        method.instructions.clear()
        method.tryCatchBlocks?.clear()
        method.localVariables?.clear()

        method.instructions = instructions {
            INVOKESTATIC(loaderInternalName, "load", "()V")
            if (isStatic) {
                LDC(Type.getObjectType(classNode.name))
            } else {
                ALOAD(0)
            }

            var localIndex = if (isStatic) 0 else 1
            argumentTypes.forEach { argument ->
                +VarInsnNode(argument.getOpcode(Opcodes.ILOAD), localIndex)
                localIndex += argument.size
            }

            INVOKESTATIC(loaderInternalName, binding.registeredName, binding.registeredDesc)
            +InsnNode(returnType.getOpcode(Opcodes.IRETURN))
        }

        val argumentSlots = argumentTypes.sumOf { it.size }
        method.maxStack = maxOf(1 + argumentSlots, returnType.size)
        method.maxLocals = argumentSlots + if (isStatic) 0 else 1
    }

    private fun rewriteClassInitializer(
        classPlan: NativeClassPlan,
        loaderInternalName: String,
        binding: NativeMethodBinding
    ) {
        val classNode = classPlan.classNode
        val clinit = binding.method.methodNode
        clinit.access = Opcodes.ACC_STATIC
        clinit.instructions.clear()
        clinit.tryCatchBlocks?.clear()
        clinit.localVariables?.clear()
        clinit.instructions = instructions {
            +registrationInstructions(classPlan, loaderInternalName)
            INVOKESTATIC(classNode.name, binding.registeredName, binding.registeredDesc)
            RETURN
        }
        clinit.maxStack = 2
        clinit.maxLocals = 0
    }

    private fun injectRegistration(classPlan: NativeClassPlan, loaderInternalName: String) {
        val classNode = classPlan.classNode
        val clinit = classNode.methods.firstOrNull { it.name == "<clinit>" && it.desc == "()V" }
            ?: method(
                STATIC,
                "<clinit>",
                "()V"
            ) {
                INSTRUCTIONS {
                    RETURN
                }
                MAXS(0, 0)
            }.also {
                classNode.methods.add(it)
            }

        val prefix = registrationInstructions(classPlan, loaderInternalName)
        clinit.instructions.insert(prefix)
        clinit.maxStack = maxOf(clinit.maxStack, 2)
    }

    private fun registrationInstructions(classPlan: NativeClassPlan, loaderInternalName: String): InsnList {
        val classNode = classPlan.classNode
        return instructions {
            INVOKESTATIC(loaderInternalName, "load", "()V")
            LDC(classPlan.classId)
            LDC(Type.getObjectType(classNode.name))
            INVOKESTATIC(loaderInternalName, "registerNativesForClass", "(ILjava/lang/Class;)V")
        }
    }

    private val NativeMethodCommitKind.isLoaderProxy: Boolean
        get() = this == NativeMethodCommitKind.InterfaceProxy ||
            this == NativeMethodCommitKind.InterfaceClassInitializerProxy
}
