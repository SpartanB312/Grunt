package net.spartanb312.grunt.ir.ssa.ast

import net.spartanb312.grunt.ir.ssa.core.SSABinaryInstruction
import net.spartanb312.grunt.ir.ssa.core.SSABlock
import net.spartanb312.grunt.ir.ssa.core.SSABoolType
import net.spartanb312.grunt.ir.ssa.core.SSABranchTerminator
import net.spartanb312.grunt.ir.ssa.core.SSACompareInstruction
import net.spartanb312.grunt.ir.ssa.core.SSAFunction
import net.spartanb312.grunt.ir.ssa.core.SSAFunctionSymbol
import net.spartanb312.grunt.ir.ssa.core.SSAI32Type
import net.spartanb312.grunt.ir.ssa.core.SSAIdAllocator
import net.spartanb312.grunt.ir.ssa.core.SSAParameter
import net.spartanb312.grunt.ir.ssa.core.SSAReturnTerminator
import net.spartanb312.grunt.ir.ssa.core.SSAVerifier
import net.spartanb312.grunt.ir.ssa.core.SSAVoidType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class SSAAstBuilderTest {
    @Test
    fun emitsExpressionSugarAsLinearSsaInstructions() {
        val ids = SSAIdAllocator()
        val parameter = SSAParameter(ids.valueId(), 0, SSAI32Type, "x")
        val symbol = SSAFunctionSymbol(ids.symbolId(), "test", listOf(SSAI32Type), SSAVoidType)
        val entry = SSABlock(ids.blockId())
        val trueBlock = SSABlock(ids.blockId())
        val falseBlock = SSABlock(ids.blockId())
        val function = SSAFunction(
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

        SSAVerifier.verify(function).requireValid()

        assertEquals(3, entry.instructions.size)
        assertIs<SSABinaryInstruction>(entry.instructions[0])
        assertIs<SSABinaryInstruction>(entry.instructions[1])
        val compare = assertIs<SSACompareInstruction>(entry.instructions[2])
        assertEquals(SSABoolType, compare.result.type)
        assertEquals("condition", compare.result.debugName)

        val terminator = assertIs<SSABranchTerminator>(entry.terminator)
        assertSame(compare.result, terminator.condition)
        assertSame(trueBlock, terminator.trueTarget.block)
        assertSame(falseBlock, terminator.falseTarget.block)
        assertIs<SSAReturnTerminator>(trueBlock.terminator)
        assertIs<SSAReturnTerminator>(falseBlock.terminator)
    }
}
