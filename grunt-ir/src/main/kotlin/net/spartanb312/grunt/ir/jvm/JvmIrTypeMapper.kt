package net.spartanb312.grunt.ir.jvm

import net.spartanb312.grunt.ir.core.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.analysis.BasicValue

class JvmIrTypeMapper(
    private val ids: IrIdAllocator,
    private val metadata: JvmIrMetadata
) {
    private val typeSymbols = linkedMapOf<String, IrTypeSymbol>()

    fun methodParameterTypes(desc: String, includeReceiver: IrType? = null): List<IrType> {
        val params = mutableListOf<IrType>()
        if (includeReceiver != null) params += includeReceiver
        params += Type.getArgumentTypes(desc).map { type(it) }
        return params
    }

    fun methodReturnType(desc: String): IrType {
        return type(Type.getReturnType(desc))
    }

    fun objectType(internalName: String, nullable: Boolean = true): IrRefType {
        return IrRefType(typeSymbol(internalName), nullable)
    }

    fun type(asmType: Type): IrType {
        return when (asmType.sort) {
            Type.VOID -> IrVoidType
            Type.BOOLEAN -> IrBoolType
            Type.CHAR -> IrCharType
            Type.BYTE -> IrI8Type
            Type.SHORT -> IrI16Type
            Type.INT -> IrI32Type
            Type.FLOAT -> IrF32Type
            Type.LONG -> IrI64Type
            Type.DOUBLE -> IrF64Type
            Type.ARRAY -> {
                val elementType = type(asmType.elementType)
                IrArrayType(elementType, asmType.dimensions)
            }
            Type.OBJECT -> objectType(asmType.internalName)
            Type.METHOD -> IrOpaqueType(asmType.descriptor)
            else -> IrUnknownType
        }
    }

    fun frameType(value: BasicValue?): IrType? {
        if (value == null || value == BasicValue.UNINITIALIZED_VALUE || value == BasicValue.RETURNADDRESS_VALUE) {
            return null
        }

        return when (value) {
            BasicValue.INT_VALUE -> IrI32Type
            BasicValue.FLOAT_VALUE -> IrF32Type
            BasicValue.LONG_VALUE -> IrI64Type
            BasicValue.DOUBLE_VALUE -> IrF64Type
            BasicValue.REFERENCE_VALUE -> IrRefType()
            else -> value.type?.let { type(it) } ?: IrUnknownType
        }
    }

    fun typeFromDescriptor(desc: String): IrType {
        return type(Type.getType(desc))
    }

    private fun typeSymbol(internalName: String): IrTypeSymbol {
        return typeSymbols.getOrPut(internalName) {
            IrTypeSymbol(ids.symbolId(), internalName).also {
                metadata.types[it.id] = JvmTypeMetadata(internalName, "L$internalName;")
            }
        }
    }
}
