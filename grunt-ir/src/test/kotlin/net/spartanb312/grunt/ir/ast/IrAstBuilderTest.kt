package net.spartanb312.grunt.ir.ast

import net.spartanb312.grunt.ir.core.IrBinaryInstruction
import net.spartanb312.grunt.ir.core.IrBlock
import net.spartanb312.grunt.ir.core.IrBoolType
import net.spartanb312.grunt.ir.core.IrBranchTerminator
import net.spartanb312.grunt.ir.core.IrCompareInstruction
import net.spartanb312.grunt.ir.core.IrFunction
import net.spartanb312.grunt.ir.core.IrFunctionSymbol
import net.spartanb312.grunt.ir.core.IrI32Type
import net.spartanb312.grunt.ir.core.IrIdAllocator
import net.spartanb312.grunt.ir.core.IrParameter
import net.spartanb312.grunt.ir.core.IrReturnTerminator
import net.spartanb312.grunt.ir.core.IrVerifier
import net.spartanb312.grunt.ir.core.IrVoidType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class IrAstBuilderTest {
    @Test
    fun emitsExpressionSugarAsLinearSsaInstructions() {
        val ids = IrIdAllocator()
        val parameter = IrParameter(ids.valueId(), 0, IrI32Type, "x")
        val symbol = IrFunctionSymbol(ids.symbolId(), "test", listOf(IrI32Type), IrVoidType)
        val entry = IrBlock(ids.blockId())
        val trueBlock = IrBlock(ids.blockId())
        val falseBlock = IrBlock(ids.blockId())
        val function = IrFunction(
            symbol,
            mutableListOf(parameter),
            mutableListOf(entry, trueBlock, falseBlock),
            entry
        )

        entry.ast(ids) {
            val mixed = xor(add(expr(parameter), int(1)), int(7), "mixed")
            val condition = eq(mixed, int(42), "condition")
            branch(condition, trueBlock, falseBlock)
        }
        trueBlock.ast(ids) { returnVoid() }
        falseBlock.ast(ids) { returnVoid() }

        IrVerifier.verify(function).requireValid()

        assertEquals(3, entry.instructions.size)
        assertIs<IrBinaryInstruction>(entry.instructions[0])
        assertIs<IrBinaryInstruction>(entry.instructions[1])
        val compare = assertIs<IrCompareInstruction>(entry.instructions[2])
        assertEquals(IrBoolType, compare.result.type)
        assertEquals("condition", compare.result.debugName)

        val terminator = assertIs<IrBranchTerminator>(entry.terminator)
        assertSame(compare.result, terminator.condition)
        assertSame(trueBlock, terminator.trueTarget.block)
        assertSame(falseBlock, terminator.falseTarget.block)
        assertIs<IrReturnTerminator>(trueBlock.terminator)
        assertIs<IrReturnTerminator>(falseBlock.terminator)
    }
}
