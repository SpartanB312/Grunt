package net.spartanb312.grunt.ir.jvm

import net.spartanb312.grunt.ir.core.IrExternalRef
import net.spartanb312.grunt.ir.core.IrExternalRefKind
import net.spartanb312.grunt.ir.core.IrIdAllocator
import net.spartanb312.grunt.ir.core.IrSymbolId

class JvmIrImportContext(
    val ids: IrIdAllocator = IrIdAllocator()
) {
    val metadata = JvmIrMetadata()
    val types = JvmIrTypeMapper(ids, metadata)

    fun externalRef(kind: IrExternalRefKind, debugName: String? = null): IrExternalRef {
        return IrExternalRef(ids.externalRefId(), kind, debugName)
    }
}

class JvmIrMetadata {
    val types = linkedMapOf<IrSymbolId, JvmTypeMetadata>()
    val fields = linkedMapOf<IrExternalRef, JvmFieldMetadata>()
    val methods = linkedMapOf<IrExternalRef, JvmMethodMetadata>()
    val dynamicValues = linkedMapOf<IrExternalRef, JvmDynamicValueMetadata>()
    val dynamicCalls = linkedMapOf<IrExternalRef, JvmDynamicCallMetadata>()
}

data class JvmTypeMetadata(
    val internalName: String,
    val descriptor: String
)

data class JvmFieldMetadata(
    val owner: String,
    val name: String,
    val desc: String,
    val isStatic: Boolean
)

data class JvmMethodMetadata(
    val opcode: Int,
    val owner: String,
    val name: String,
    val desc: String,
    val isInterface: Boolean
)

data class JvmDynamicValueMetadata(
    val name: String,
    val desc: String,
    val bootstrap: JvmBootstrapMetadata
)

data class JvmDynamicCallMetadata(
    val name: String,
    val desc: String,
    val bootstrap: JvmBootstrapMetadata
)

data class JvmBootstrapMetadata(
    val handle: JvmHandleMetadata,
    val args: List<JvmBootstrapArgMetadata>
)

data class JvmHandleMetadata(
    val tag: Int,
    val owner: String,
    val name: String,
    val desc: String,
    val isInterface: Boolean
)

sealed interface JvmBootstrapArgMetadata {
    data class IntArg(val value: Int) : JvmBootstrapArgMetadata
    data class LongArg(val value: Long) : JvmBootstrapArgMetadata
    data class FloatArg(val value: Float) : JvmBootstrapArgMetadata
    data class DoubleArg(val value: Double) : JvmBootstrapArgMetadata
    data class StringArg(val value: String) : JvmBootstrapArgMetadata
    data class TypeArg(val descriptor: String) : JvmBootstrapArgMetadata
    data class HandleArg(val handle: JvmHandleMetadata) : JvmBootstrapArgMetadata
    data class DynamicValueArg(val value: JvmDynamicValueMetadata) : JvmBootstrapArgMetadata
    data class OpaqueArg(val value: String) : JvmBootstrapArgMetadata
}

data class JvmIrImportResult(
    val function: net.spartanb312.grunt.ir.core.IrFunction,
    val metadata: JvmIrMetadata
)
