package net.spartanb312.grunt.process.transformers.flow.process

import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.flow.ControlflowTransformer
import net.spartanb312.grunt.utils.builder.*
import net.spartanb312.grunt.utils.extensions.isInitializer
import net.spartanb312.grunt.utils.extensions.isPublic
import net.spartanb312.grunt.utils.extensions.isStatic
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodNode
import kotlin.random.Random

object JunkCode {

    private val nonExcludedUtilList = mutableListOf<TrashCallMethod>()
    private val allStaticUtilList = mutableSetOf<TrashCallMethod>()

    fun generate(methodNode: MethodNode, returnType: Type): InsnList {
        return if (methodNode.isInitializer) insnList {
            ACONST_NULL
            ATHROW
        } else insnList {
            when (returnType.sort) {
                Type.INT -> {
                    val trashCallMethod = findRandomGivenReturnTypeMethod(Type.INT)
                    if (ControlflowTransformer.junkCode && trashCallMethod != null) {
                        +generateTrashCall(trashCallMethod)
                    } else INT(Random.nextInt())
                    IRETURN
                }

                Type.FLOAT -> {
                    val trashCallMethod = findRandomGivenReturnTypeMethod(Type.FLOAT)
                    if (ControlflowTransformer.junkCode && trashCallMethod != null) {
                        +generateTrashCall(trashCallMethod)
                    } else FLOAT(Random.nextFloat())
                    FRETURN
                }

                Type.BOOLEAN -> {
                    val trashCallMethod = findRandomGivenReturnTypeMethod(Type.BOOLEAN)
                    if (ControlflowTransformer.junkCode && trashCallMethod != null) {
                        +generateTrashCall(trashCallMethod)
                    } else INT(if (Random.nextBoolean()) 1 else 0)
                    IRETURN
                }

                Type.CHAR -> {
                    val trashCallMethod = findRandomGivenReturnTypeMethod(Type.CHAR)
                    if (ControlflowTransformer.junkCode && trashCallMethod != null) {
                        +generateTrashCall(trashCallMethod)
                    } else INT(Random.nextInt(32767))
                    IRETURN
                }

                Type.SHORT -> {
                    val trashCallMethod = findRandomGivenReturnTypeMethod(Type.SHORT)
                    if (ControlflowTransformer.junkCode && trashCallMethod != null) {
                        +generateTrashCall(trashCallMethod)
                    } else INT(Random.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
                    IRETURN
                }

                Type.LONG -> {
                    val trashCallMethod = findRandomGivenReturnTypeMethod(Type.LONG)
                    if (ControlflowTransformer.junkCode && trashCallMethod != null) {
                        +generateTrashCall(trashCallMethod)
                    } else LDC(Random.nextLong())
                    LRETURN
                }

                Type.DOUBLE -> {
                    val trashCallMethod = findRandomGivenReturnTypeMethod(Type.DOUBLE)
                    if (ControlflowTransformer.junkCode && trashCallMethod != null) {
                        +generateTrashCall(trashCallMethod)
                    } else LDC(Random.nextDouble())
                    DRETURN
                }

                Type.VOID -> {
                    val trashCallMethod = findRandomGivenReturnTypeMethod(Type.VOID)
                    if (ControlflowTransformer.junkCode && trashCallMethod != null) {
                        +generateTrashCall(trashCallMethod)
                    }
                    RETURN
                }

                Type.OBJECT -> {
                    val trashCallMethod = findRandomGivenReturnTypeMethod(Type.VOID)
                    if (ControlflowTransformer.junkCode && trashCallMethod != null) {
                        +generateTrashCall(trashCallMethod)
                    }
                    ACONST_NULL
                    ARETURN
                }

                else -> {
                    ACONST_NULL
                    ATHROW
                }
            }
        }
    }

    fun refresh(resourceCache: ResourceCache) {
        nonExcludedUtilList.clear()
        nonExcludedUtilList.addAll(generateUtilList(resourceCache.nonExcluded))
        if (ControlflowTransformer.expandedJunkCode) {
            allStaticUtilList.clear()
            allStaticUtilList.addAll(generateUtilList(resourceCache.allClasses))
        }
    }

    private fun findRandomGivenReturnTypeMethod(sort: Int): TrashCallMethod? {
        val nonExcluded = nonExcludedUtilList.filter { it.returnType.sort == sort }.randomOrNull()
        if (!ControlflowTransformer.expandedJunkCode) return nonExcluded
        return nonExcluded ?: allStaticUtilList.filter { it.returnType.sort == sort }.randomOrNull()
    }

    private fun generateUtilList(classNodes: Collection<ClassNode>): Set<TrashCallMethod> {
        val staticUtilMethods = mutableSetOf<TrashCallMethod>()
        classNodes.forEach { classNode ->
            classNode.methods.forEach { methodNode ->
                val loadTypes = Type.getArgumentTypes(methodNode.desc)
                if (classNode.isPublic && methodNode.isStatic && methodNode.isPublic && loadTypes.all {
                        it.sort == Type.INT
                                || it.sort == Type.FLOAT
                                || it.sort == Type.BOOLEAN
                                || it.sort == Type.CHAR
                                || it.sort == Type.SHORT
                                || it.sort == Type.LONG
                                || it.sort == Type.DOUBLE
                    }
                ) staticUtilMethods.add(
                    TrashCallMethod(
                        classNode,
                        methodNode,
                        loadTypes,
                        Type.getReturnType(methodNode.desc)
                    )
                )
            }
        }
        return staticUtilMethods
    }

    private fun generateTrashCall(target: TrashCallMethod): InsnList {
        return insnList {
            if (target.loadTypes.isNotEmpty()) target.loadTypes.forEach { type: Type ->
                when (type.sort) {
                    Type.INT -> INT(Random.nextInt())
                    Type.FLOAT -> FLOAT(Random.nextFloat())
                    Type.BOOLEAN -> INT(if (Random.nextBoolean()) 1 else 0)
                    Type.CHAR -> INT(Random.nextInt(32767))
                    Type.SHORT -> INT(Random.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
                    Type.LONG -> LDC(Random.nextLong())
                    Type.DOUBLE -> LDC(Random.nextDouble())
                    else -> throw Exception("Impossible")
                }
            }
            INVOKESTATIC(target.owner.name, target.methodNode.name, target.methodNode.desc)
        }
    }

    private data class TrashCallMethod(
        val owner: ClassNode,
        val methodNode: MethodNode,
        val loadTypes: Array<Type>,
        val returnType: Type
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TrashCallMethod

            if (owner != other.owner) return false
            if (methodNode != other.methodNode) return false
            if (!loadTypes.contentEquals(other.loadTypes)) return false
            return returnType == other.returnType
        }

        override fun hashCode(): Int {
            var result = owner.hashCode()
            result = 31 * result + methodNode.hashCode()
            result = 31 * result + loadTypes.contentHashCode()
            result = 31 * result + returnType.hashCode()
            return result
        }
    }

}