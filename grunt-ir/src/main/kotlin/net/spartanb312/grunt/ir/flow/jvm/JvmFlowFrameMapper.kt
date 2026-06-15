package net.spartanb312.grunt.ir.flow.jvm

import net.spartanb312.grunt.ir.flow.core.FlowFrame
import net.spartanb312.grunt.ir.flow.core.FlowFrameValue
import org.objectweb.asm.Type
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame

object JvmFlowFrameMapper {
    fun frame(frame: Frame<BasicValue>?): FlowFrame {
        if (frame == null) return FlowFrame.Empty

        val locals = List(frame.locals) { index ->
            value(frame.getLocal(index))
        }.trimTrailingTop()
        val stack = List(frame.stackSize) { index ->
            value(frame.getStack(index))
        }

        return FlowFrame(locals, stack)
    }

    fun value(value: BasicValue?): FlowFrameValue {
        return when (value) {
            null,
            BasicValue.UNINITIALIZED_VALUE -> FlowFrameValue.Top
            BasicValue.INT_VALUE -> FlowFrameValue.Int
            BasicValue.FLOAT_VALUE -> FlowFrameValue.Float
            BasicValue.LONG_VALUE -> FlowFrameValue.Long
            BasicValue.DOUBLE_VALUE -> FlowFrameValue.Double
            BasicValue.REFERENCE_VALUE -> FlowFrameValue.Object("java/lang/Object")
            BasicValue.RETURNADDRESS_VALUE -> FlowFrameValue.Unknown("returnAddress")
            else -> if (value.type?.sort == Type.OBJECT && value.type?.internalName == JvmFlowNullInternalName) {
                FlowFrameValue.Null
            } else {
                value.type?.let(::type) ?: FlowFrameValue.Unknown()
            }
        }
    }

    fun type(type: Type): FlowFrameValue {
        return when (type.sort) {
            Type.BOOLEAN,
            Type.CHAR,
            Type.BYTE,
            Type.SHORT,
            Type.INT -> FlowFrameValue.Int
            Type.FLOAT -> FlowFrameValue.Float
            Type.LONG -> FlowFrameValue.Long
            Type.DOUBLE -> FlowFrameValue.Double
            Type.ARRAY -> FlowFrameValue.Object(type.descriptor)
            Type.OBJECT -> FlowFrameValue.Object(type.internalName)
            else -> FlowFrameValue.Unknown(type.descriptor)
        }
    }

    private fun List<FlowFrameValue>.trimTrailingTop(): List<FlowFrameValue> {
        var end = size
        while (end > 0 && this[end - 1] == FlowFrameValue.Top) {
            end--
        }
        return take(end)
    }
}
