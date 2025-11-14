package tests

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.objectweb.asm.tree.ClassNode
import kotlin.test.Test

@Suppress("TestFunctionName")
class MoreTest {
    @Test
    fun Object() {
        doTest(Object::class.java)
    }

    @Test
    fun ArrayList() {
        doTest(ArrayList::class.java)
    }

    @Test
    fun HashMap() {
        doTest(HashMap::class.java)
    }

    @Test
    fun ClassNode() {
        doTest(ClassNode::class.java)
    }

    @Test
    fun Object2ObjectOpenHashMap() {
        doTest(Object2ObjectOpenHashMap::class.java)
    }

    @Test
    fun NodeFactory() {
        doTest(net.spartanb312.grunteon.asm.tree.NodeFactory::class.java)
    }

    @Test
    fun NodeFactoryDefault() {
        doTest(net.spartanb312.grunteon.asm.tree.NodeFactory.Default::class.java)
    }
}