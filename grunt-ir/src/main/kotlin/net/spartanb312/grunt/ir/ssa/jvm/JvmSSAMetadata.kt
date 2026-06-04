package net.spartanb312.grunt.ir.ssa.jvm

import net.spartanb312.grunt.ir.ssa.core.SSAExternalRef
import net.spartanb312.grunt.ir.ssa.core.SSAExternalRefKind
import net.spartanb312.grunt.ir.ssa.core.SSAFunction
import net.spartanb312.grunt.ir.ssa.core.SSAIdAllocator
import net.spartanb312.grunt.ir.ssa.core.SSASymbolId

class JvmSSAImportContext(
    val ids: SSAIdAllocator = SSAIdAllocator()
) {
    val metadata = JvmSSAMetadata()
    val types = JvmSSATypeMapper(ids, metadata)

    fun externalRef(kind: SSAExternalRefKind, debugName: String? = null): SSAExternalRef {
        return SSAExternalRef(ids.externalRefId(), kind, debugName)
    }
}

class JvmSSAMetadata {
    val types = linkedMapOf<SSASymbolId, JvmTypeMetadata>()
    val fields = linkedMapOf<SSAExternalRef, JvmFieldMetadata>()
    val methods = linkedMapOf<SSAExternalRef, JvmMethodMetadata>()
    val dynamicValues = linkedMapOf<SSAExternalRef, JvmDynamicValueMetadata>()
    val dynamicCalls = linkedMapOf<SSAExternalRef, JvmDynamicCallMetadata>()
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

data class JvmSSAImportResult(
    val function: SSAFunction,
    val metadata: JvmSSAMetadata
)
