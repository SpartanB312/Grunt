package tests

import net.spartanb312.grunteon.testcase.methodrename.OverlapInterface2To1
import net.spartanb312.grunteon.testcase.methodrename.OverlapInterface3To2
import net.spartanb312.grunteon.testcase.methodrename.OverlapInterface3To2To1
import net.spartanb312.grunteon.testcase.methodrename.OverloadShadow1
import kotlin.test.Test

@Suppress("TestFunctionName")
class BasicTest {
    @Test
    fun OverlapInterface2To1() {
        doTest(OverlapInterface2To1::class.java)
    }

    @Test
    fun OverlapInterface3To2() {
        doTest(OverlapInterface3To2::class.java)
    }

    @Test
    fun OverlapInterface3To2To1() {
        doTest(OverlapInterface3To2To1::class.java)
    }

    @Test
    fun OverloadShadow1() {
        doTest(OverloadShadow1::class.java)
    }
}