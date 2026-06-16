package net.spartanb312.grunteon.obfuscator.util

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.CreditCounter
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.hierarchy.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy.MethodHierarchy
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InvokeDynamicInsnNode

object IndyChecker {
    context(_: PipelineBuilder)
    fun check(
        creditCounter: CreditCounter,
        hierarchyInfo: ScopeValueKey.Global<MethodHierarchy>,
        infoMappings: ScopeValueKey.Global<out Int2ObjectMap<String>>
    ) {
        val counter = reducibleScopeValue { MergeableCounter() }
        parForEachClasses { classNode ->
            val mh = hierarchyInfo.global
            val ch = mh.classHierarchy
            val counter = counter.local
            context(ch, mh) {
                val mappings = infoMappings.global
                classNode.methods?.forEach { methodNode ->
                    methodNode.instructions?.forEach { insnNode ->
                        if (insnNode is InvokeDynamicInsnNode) {
                            checkInsn(counter, insnNode, mappings)
                        }
                    }
                }
            }
        }
        post {
            creditCounter.add(counter.global.get() * 1000L)
            Logger.info("    Generated indy mapping for ${counter.global.get()} methods")
        }
    }

    context(instance: Grunteon, ch: ClassHierarchy, mh: MethodHierarchy)
    private fun checkInsn(
        counter: MergeableCounter,
        invokeDynamicInsnNode: InvokeDynamicInsnNode,
        infoMappings: Int2ObjectMap<String> // Method, new name, desc){}
    ) {
        if (invokeDynamicInsnNode.bsmArgs == null) return
        val handle = invokeDynamicInsnNode.bsmArgs.lastOrNull { it is Handle } as? Handle? ?: return
        val handleCodename = handle.name + handle.desc
        val handleMethodCode = mh.methodCodeLookup.getInt(handleCodename)
        if (handleMethodCode == -1) return

        val insnName = invokeDynamicInsnNode.name
        val insnOwner = invokeDynamicInsnNode.desc.substringAfter(")L").removeSuffix(";")
        val indyParams = invokeDynamicInsnNode.desc.substringAfter("(").substringBeforeLast(")")
        val originParams = handle.desc.substringAfter("(").substringBeforeLast(")")
        val remainParams = originParams.removePrefix(indyParams)
        val insnDesc = "(" + remainParams + ")" + handle.desc.substringAfterLast(")")
        val insnTypes = Type.getArgumentTypes(insnDesc)

        val insnOwnerClass = ch.findClassEntry(insnOwner)
        if (!insnOwnerClass.isValid) return
        val insnOwnerMethods = insnOwnerClass.methods
        val shouldRemap = mh.methodCodeToMethods[handleMethodCode].any { prev ->
            when (handle.tag) {
                Opcodes.H_INVOKEVIRTUAL -> ch.isSubType(handle.owner, prev.owner.name)
                Opcodes.H_INVOKESTATIC -> ch.isSubType(handle.owner, prev.owner.name)
                else -> handle.owner == prev.owner.name
            }
        }
        if (!shouldRemap) return
        insnOwnerMethods.forEach { preMethod ->
            if (preMethod.name != insnName) return@forEach
            var typesMatch = preMethod.desc == insnDesc
            if (!typesMatch) {
                val paramsTypes = Type.getArgumentTypes(preMethod.desc)
                typesMatch = paramsTypes.size == insnTypes.size
                if (typesMatch) {
                    for (index in paramsTypes.indices) {
                        val type1 = insnTypes[index]
                        val type2 = paramsTypes[index]
                        if (!(type1.className == type2.className || type2.className == "java.lang.Object")) {
                            typesMatch = false
                        }
                    }
                }
            }
            if (!typesMatch) return@forEach

            val newName = infoMappings.get(preMethod.index) ?: return@forEach

            instance.nameMapping.putIndyMapping(insnName, insnDesc, newName)
            counter.add()
        }
    }
}