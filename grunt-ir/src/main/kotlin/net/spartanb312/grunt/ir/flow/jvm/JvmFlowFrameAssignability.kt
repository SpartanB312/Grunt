package net.spartanb312.grunt.ir.flow.jvm

import net.spartanb312.grunt.ir.flow.core.FlowFrameValue
import net.spartanb312.grunt.ir.flow.core.FlowFrames
import org.objectweb.asm.Type

internal class JvmFlowFrameAssignability(
    private val hierarchy: JvmFlowTypeHierarchy
) {
    fun isAssignable(actual: FlowFrameValue, expected: FlowFrameValue): Boolean {
        if (FlowFrames.isAssignable(actual, expected)) return true
        if (actual !is FlowFrameValue.Object || expected !is FlowFrameValue.Object) return false
        return isReferenceAssignable(actual.internalName, expected.internalName)
    }

    private fun isReferenceAssignable(actualName: String, expectedName: String): Boolean {
        val actualType = referenceType(actualName)
        val expectedType = referenceType(expectedName)
        return when {
            actualType.sort == Type.ARRAY && expectedType.sort == Type.ARRAY -> {
                isArrayAssignable(actualType, expectedType)
            }
            actualType.sort == Type.ARRAY && expectedType.sort == Type.OBJECT -> {
                expectedType.internalName == JavaObjectInternalName ||
                    expectedType.internalName == JavaCloneableInternalName ||
                    expectedType.internalName == JavaSerializableInternalName
            }
            actualType.sort == Type.OBJECT && expectedType.sort == Type.OBJECT -> {
                hierarchy.isSubType(actualType.internalName, expectedType.internalName)
            }
            else -> false
        }
    }

    private fun isArrayAssignable(actualType: Type, expectedType: Type): Boolean {
        if (actualType == expectedType) return true

        val actualElement = actualType.oneDimensionElementType()
        val expectedElement = expectedType.oneDimensionElementType()
        if (!actualElement.isReferenceType() || !expectedElement.isReferenceType()) {
            return false
        }

        return isReferenceAssignable(actualElement.referenceName(), expectedElement.referenceName())
    }

    private fun referenceType(name: String): Type {
        return if (name.startsWith("[")) Type.getType(name) else Type.getObjectType(name)
    }

    private fun Type.oneDimensionElementType(): Type {
        return Type.getType(descriptor.substring(1))
    }

    private fun Type.isReferenceType(): Boolean {
        return sort == Type.OBJECT || sort == Type.ARRAY
    }

    private fun Type.referenceName(): String {
        return if (sort == Type.ARRAY) descriptor else internalName
    }
}
