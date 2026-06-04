package net.spartanb312.grunt.ir.ssa.jvm

import net.spartanb312.grunt.ir.ssa.core.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.analysis.BasicValue

class JvmSSATypeMapper(
    private val ids: SSAIdAllocator,
    private val metadata: JvmSSAMetadata
) {
    private val typeSymbols = linkedMapOf<String, SSATypeSymbol>()

    fun methodParameterTypes(desc: String, includeReceiver: SSAType? = null): List<SSAType> {
        val params = mutableListOf<SSAType>()
        if (includeReceiver != null) params += includeReceiver
        params += Type.getArgumentTypes(desc).map { type(it) }
        return params
    }

    fun methodReturnType(desc: String): SSAType {
        return type(Type.getReturnType(desc))
    }

    fun objectType(internalName: String, nullable: Boolean = true): SSARefType {
        return SSARefType(typeSymbol(internalName), nullable)
    }

    fun type(asmType: Type): SSAType {
        return when (asmType.sort) {
            Type.VOID -> SSAVoidType
            Type.BOOLEAN -> SSABoolType
            Type.CHAR -> SSACharType
            Type.BYTE -> SSAI8Type
            Type.SHORT -> SSAI16Type
            Type.INT -> SSAI32Type
            Type.FLOAT -> SSAF32Type
            Type.LONG -> SSAI64Type
            Type.DOUBLE -> SSAF64Type
            Type.ARRAY -> {
                val elementType = type(asmType.elementType)
                SSAArrayType(elementType, asmType.dimensions)
            }
            Type.OBJECT -> objectType(asmType.internalName)
            Type.METHOD -> SSAOpaqueType(asmType.descriptor)
            else -> SSAUnknownType
        }
    }

    fun frameType(value: BasicValue?): SSAType? {
        if (value == null || value == BasicValue.UNINITIALIZED_VALUE || value == BasicValue.RETURNADDRESS_VALUE) {
            return null
        }

        return when (value) {
            BasicValue.INT_VALUE -> SSAI32Type
            BasicValue.FLOAT_VALUE -> SSAF32Type
            BasicValue.LONG_VALUE -> SSAI64Type
            BasicValue.DOUBLE_VALUE -> SSAF64Type
            BasicValue.REFERENCE_VALUE -> SSARefType()
            else -> value.type?.let { type(it) } ?: SSAUnknownType
        }
    }

    fun typeFromDescriptor(desc: String): SSAType {
        return type(Type.getType(desc))
    }

    private fun typeSymbol(internalName: String): SSATypeSymbol {
        return typeSymbols.getOrPut(internalName) {
            SSATypeSymbol(ids.symbolId(), internalName).also {
                metadata.types[it.id] = JvmTypeMetadata(internalName, "L$internalName;")
            }
        }
    }
}
