package tests

import net.spartanb312.grunteon.asm.tree.NodeFactory
import net.spartanb312.grunteon.asm.tree.toGrunt
import net.spartanb312.grunteon.asm.tree.toOw2
import net.spartanb312.grunteon.testcase.methodrename.OverlapInterface2To1
import org.objectweb.asm.ClassWriter
import readClassNode
import kotlin.test.assertContentEquals

fun doTest(clazz: Class<*>) {
    val inputClass1Ow2 = readClassNode(OverlapInterface2To1::class.java)
    val writer1 = ClassWriter(0)
    inputClass1Ow2.accept(writer1)
    val expect = writer1.toByteArray()

    val inputClass2Ow2 = readClassNode(OverlapInterface2To1::class.java)
    val inputClass2 = inputClass2Ow2.toGrunt(NodeFactory.Default)
    val outputClassOw2 = inputClass2.toOw2()
    val writer2 = ClassWriter(0)
    outputClassOw2.accept(writer2)
    val actual = writer2.toByteArray()

    assertContentEquals(expect, actual)
}